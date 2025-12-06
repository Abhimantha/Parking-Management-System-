package com.example.parking.controller;

import com.example.parking.entity.Payment;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.PaymentPhase;
import com.example.parking.entity.enums.PaymentStatus;
import com.example.parking.repository.PaymentRepository;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.service.FeatureGate;
import com.example.parking.service.PaymentService;
import com.example.parking.service.PricingEngineService;
import com.example.parking.service.SystemSettingsService;
import com.example.parking.web.FeatureDisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ReservationRepository reservationRepo;
    private final PaymentRepository paymentRepo;
    private final SystemSettingsService settings;
    private final FeatureGate gate;
    private final PricingEngineService pricing;

    public PaymentController(PaymentService paymentService,
                             ReservationRepository reservationRepo,
                             PaymentRepository paymentRepo,
                             SystemSettingsService settings,
                             FeatureGate gate,
                             PricingEngineService pricing) {
        this.paymentService = paymentService;
        this.reservationRepo = reservationRepo;
        this.paymentRepo = paymentRepo;
        this.settings = settings;
        this.gate = gate;
        this.pricing = pricing;
    }

    @GetMapping
    public String list(Authentication auth, Model model,
                       @RequestParam(required = false) String msg,
                       @RequestParam(required = false) String err) {

        final String email = (auth == null ? null : auth.getName());
        final boolean isAdminOrManager = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String role = a.getAuthority();
                    return "ROLE_ADMIN".equals(role) || "ROLE_MANAGER".equals(role);
                });

        List<Payment> payments = isAdminOrManager ? paymentService.findAll() : safeFindPaymentsForEmail(email);

        Map<Long, List<Payment>> grouped = payments.stream()
                .filter(p -> p.getReservation() != null && p.getReservation().getId() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getReservation().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (List<Payment> group : grouped.values()) {
            group.sort(Comparator.comparing(p -> {
                LocalDateTime t = p.getCreatedAt();
                return t == null ? LocalDateTime.MIN : t;
            }));
        }

        Map<Long, BigDecimal> paidTotals = new HashMap<>();
        for (Map.Entry<Long, List<Payment>> e : grouped.entrySet()) {
            BigDecimal sum = e.getValue().stream()
                    .filter(p -> p.getStatus() == PaymentStatus.PAID)
                    .map(Payment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            paidTotals.put(e.getKey(), sum);
        }

        model.addAttribute("payments", payments);
        model.addAttribute("groupedPayments", grouped);
        model.addAttribute("paidTotals", paidTotals);
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        model.addAttribute("refundWindowHours", settings.get().getRefundWindowHours());
        return "payments";
    }

    /* =========================  CHECKOUT (QUERY)  ========================= */

    @GetMapping("/checkout")
    public String checkout(@RequestParam Long reservationId,
                           @RequestParam(defaultValue = "ADVANCE") PaymentPhase phase,
                           @RequestParam(required = false) String promo,
                           Model model, Authentication auth, RedirectAttributes ra) {

        try { gate.ensurePaymentsEnabled(); }
        catch (FeatureDisabledException ex) { ra.addFlashAttribute("err", ex.getMessage()); return "redirect:/reservations"; }

        Reservation r = reservationRepo.findById(reservationId).orElse(null);
        if (r == null) { ra.addFlashAttribute("err", "Reservation not found"); return "redirect:/reservations"; }

        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isOwner = auth != null && r.getUser() != null
                && r.getUser().getEmail() != null
                && r.getUser().getEmail().equalsIgnoreCase(auth.getName());
        if (!isAdmin && !isOwner) { ra.addFlashAttribute("err", "Not authorized"); return "redirect:/reservations"; }

        final Slot slot = r.getSlot();
        if (slot == null || r.getStartTime() == null || r.getEndTime() == null) {
            ra.addFlashAttribute("err", "Reservation is incomplete.");
            return "redirect:/reservations";
        }

        // promo code precedence: request > persisted-on-reservation
        String requestPromo = (promo == null || promo.isBlank()) ? null : promo.trim();
        String persistedPromo = safeGetPromo(r);
        String appliedPromo = (requestPromo != null) ? requestPromo : persistedPromo;

        // Use the same engine as /api/pricing/quote so UI and API match 100%
        BigDecimal quotedTotal = round2(
                (appliedPromo == null)
                        ? pricing.quote(slot, r.getStartTime(), r.getEndTime()).total
                        : pricing.quote(slot, r.getStartTime(), r.getEndTime(), appliedPromo).total
        );

        // persist promo if newly provided
        if (appliedPromo != null && (persistedPromo == null || !persistedPromo.equalsIgnoreCase(appliedPromo))) {
            try { r.setPromoCode(appliedPromo); reservationRepo.save(r); } catch (Exception ignored) {}
        }

        BigDecimal paidSoFar = round2(paymentService.totalPaid(reservationId));
        BigDecimal amountDue;

        if (phase == PaymentPhase.ADVANCE) {
            if (paidSoFar.compareTo(BigDecimal.ZERO) > 0) {
                ra.addFlashAttribute("msg", "Advance already paid.");
                return "redirect:/payments";
            }
            amountDue = round2(quotedTotal.multiply(resolveAdvanceRate()));
        } else {
            amountDue = round2(quotedTotal.subtract(paidSoFar).max(BigDecimal.ZERO));
            if (amountDue.compareTo(BigDecimal.ZERO) <= 0) {
                ra.addFlashAttribute("msg", "No balance due for this reservation.");
                return "redirect:/payments";
            }
        }

        Payment p = (phase == PaymentPhase.ADVANCE)
                ? paymentService.ensureAdvance(reservationId, amountDue)
                : paymentService.ensureBalance(reservationId, amountDue);

        if (p.getStatus() == PaymentStatus.PAID) {
            ra.addFlashAttribute("msg", "Payment already completed.");
            return "redirect:/payments";
        }

        model.addAttribute("payment", p);
        model.addAttribute("phase", phase.name());
        model.addAttribute("promo", appliedPromo == null ? "" : appliedPromo);
        model.addAttribute("total", quotedTotal);
        model.addAttribute("amountDue", amountDue);
        model.addAttribute("amount", amountDue); // existing template key

        return "checkout";
    }

    /* =====================  CHECKOUT (PATH)  ===================== */
    // Works for BOTH: /payments/{paymentId}/checkout  and  /payments/{reservationId}/checkout
    @GetMapping("/{id}/checkout")
    public String checkoutPath(@PathVariable Long id,
                               @RequestParam(defaultValue = "ADVANCE") PaymentPhase phase,
                               @RequestParam(required = false) String promo) {

        Long reservationId = null;

        // 1) If it's a payment id, resolve its reservation
        try {
            Optional<Payment> pp = paymentRepo.findById(id);
            if (pp.isPresent() && pp.get().getReservation() != null) {
                reservationId = pp.get().getReservation().getId();
            }
        } catch (Exception ignored) {}

        // 2) If not a payment, maybe it's a reservation id
        if (reservationId == null && reservationRepo.findById(id).isPresent()) {
            reservationId = id;
        }

        if (reservationId == null) {
            return "redirect:/payments?err=" + enc("Unknown payment/reservation id: " + id);
        }

        String url = "/payments/checkout?reservationId=" + reservationId
                + "&phase=" + phase.name()
                + (promo == null || promo.isBlank() ? "" : "&promo=" + enc(promo.trim()));
        return "redirect:" + url;
    }

    /* ========================  PAY & CANCEL  ======================== */

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, @RequestParam String method, RedirectAttributes ra) {
        try { gate.ensurePaymentsEnabled(); }
        catch (FeatureDisabledException ex) { ra.addFlashAttribute("err", ex.getMessage()); return "redirect:/payments"; }

        paymentService.markPaid(id, method);
        ra.addFlashAttribute("msg", "Payment successful");
        return "redirect:/payments";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        try { gate.ensureCancellationAllowed(reason); }
        catch (FeatureDisabledException ex) { ra.addFlashAttribute("err", ex.getMessage()); return "redirect:/payments"; }

        paymentService.cancel(id, reason);
        ra.addFlashAttribute("msg", "Payment cancelled. Refund will be processed within 24 hours.");
        return "redirect:/payments";
    }

    /* ---------------- helpers ---------------- */

    private static BigDecimal round2(BigDecimal v){
        if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveAdvanceRate() {
        try {
            Object cfg = settings.get();
            if (cfg != null) {
                try {
                    Method m = cfg.getClass().getMethod("getAdvancePercent");
                    Object v = m.invoke(cfg);
                    if (v instanceof Number n) {
                        int pct = n.intValue();
                        if (pct >= 0 && pct <= 100) {
                            return BigDecimal.valueOf(pct).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    Method m2 = cfg.getClass().getMethod("getAdvanceRate");
                    Object v2 = m2.invoke(cfg);
                    if (v2 instanceof Number n2) {
                        BigDecimal rate = BigDecimal.valueOf(n2.doubleValue());
                        if (rate.compareTo(BigDecimal.ZERO) >= 0 && rate.compareTo(BigDecimal.ONE) <= 0) {
                            return rate.setScale(4, RoundingMode.HALF_UP);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return new BigDecimal("0.30"); // default 30%
    }

    private static String safeGetPromo(Reservation r) {
        try { String v = r.getPromoCode(); return (v == null || v.isBlank()) ? null : v; }
        catch (Exception ignore) { return null; }
    }

    private static String enc(String s) { return URLEncoder.encode(String.valueOf(s), StandardCharsets.UTF_8); }

    /** Try service method by email; otherwise fall back to in-memory filter of findAll(). */
    private List<Payment> safeFindPaymentsForEmail(String email) {
        if (email == null || email.isBlank()) return Collections.emptyList();

        try {
            Method m = paymentService.getClass().getMethod("findAllByUserEmail", String.class);
            Object res = m.invoke(paymentService, email);
            if (res instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Payment> cast = (List<Payment>) list;
                return cast;
            }
        } catch (NoSuchMethodException nsme) {
            // ignore, fall through to filter
        } catch (Exception e) {
            // if service call failed, fall through to filter
        }

        // Fallback: filter in memory to just this user's reservations
        return paymentService.findAll().stream()
                .filter(p -> {
                    try {
                        return p.getReservation() != null
                                && p.getReservation().getUser() != null
                                && p.getReservation().getUser().getEmail() != null
                                && p.getReservation().getUser().getEmail().equalsIgnoreCase(email);
                    } catch (Exception ex) { return false; }
                })
                .collect(Collectors.toList());
    }
}
