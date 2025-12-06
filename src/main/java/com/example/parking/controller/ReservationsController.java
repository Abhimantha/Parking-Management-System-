package com.example.parking.controller;

import com.example.parking.entity.Payment;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.enums.ReservationStatus;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.service.FeatureGate;
import com.example.parking.service.PaymentService;
import com.example.parking.service.SystemSettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/reservations")
public class ReservationsController {

    private final ReservationRepository reservationRepo;
    private final SystemSettingsService settings;
    private final PaymentService paymentService;
    private FeatureGate gate;

    public ReservationsController(ReservationRepository reservationRepo,
                                  SystemSettingsService settings,
                                  PaymentService paymentService) {
        this.reservationRepo = reservationRepo;
        this.settings = settings;
        this.paymentService = paymentService;

    }

    @GetMapping
    public String list(Authentication auth, Model model,
                       @RequestParam(required = false) String msg,
                       @RequestParam(required = false) String err) {

        boolean isAdminOrManager = hasAnyRole(auth, "ROLE_ADMIN", "ROLE_MANAGER");

        List<Reservation> reservations = isAdminOrManager
                ? reservationRepo.findAll()
                : reservationRepo.findByUserEmailOrderByStartTimeDesc(auth.getName());

        model.addAttribute("reservations", reservations);
        model.addAttribute("canManageAll", isAdminOrManager);
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        model.addAttribute("settings", settings.get());
        return "reservations";
    }

    @PostMapping("/cancel")
    public String cancel(@RequestParam Long id,
                         @RequestParam(required = false) String reason,
                         Authentication auth) {

        gate.ensureCancellationAllowed(reason);

        if (!settings.get().isAllowCancellation()) {
            return "redirect:/reservations?err=Cancellation%20disabled";
        }
        if (settings.get().isRequireCancellationReason()
                && (reason == null || reason.isBlank())) {
            return "redirect:/reservations?err=Reason%20required%20to%20cancel";
        }

        Reservation r = reservationRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/reservations?err=Reservation%20not%20found";

        if (!hasAnyRole(auth, "ROLE_ADMIN", "ROLE_MANAGER") && !isOwner(auth, r)) {
            return "redirect:/reservations?err=Not%20authorized";
        }

        if (r.getStatus() != ReservationStatus.ACTIVE) {
            return "redirect:/reservations?err=Only%20ACTIVE%20reservations%20can%20be%20cancelled";
        }

        r.setCancelReason(reason);
        r.setCanceledAt(LocalDateTime.now());
        r.setStatus(ReservationStatus.CANCELLED);
        reservationRepo.save(r);

        paymentService.markAllPaymentsRefunded(id, reason);
        return "redirect:/reservations?msg=Reservation%20cancelled.%20Refund%20processed.";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long id,
                         @RequestParam(required = false) String start,
                         @RequestParam(required = false) String end,
                         @RequestParam(required = false) ReservationStatus status,
                         RedirectAttributes ra,
                         Authentication auth) {

        Reservation r = reservationRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/reservations?err=Reservation%20not%20found";

        if (!hasAnyRole(auth, "ROLE_ADMIN", "ROLE_MANAGER") && !isOwner(auth, r)) {
            ra.addFlashAttribute("err", "Not authorized");
            return "redirect:/reservations";
        }

        if (r.getStatus() != ReservationStatus.ACTIVE) {
            ra.addFlashAttribute("err", "Only ACTIVE reservations can be edited");
            return "redirect:/reservations";
        }

        LocalDateTime startDt = parseLocal(start);
        LocalDateTime endDt   = parseLocal(end);
        if (startDt != null) r.setStartTime(startDt);
        if (endDt != null)   r.setEndTime(endDt);

        if (status == ReservationStatus.COMPLETED) {
            r.setStatus(ReservationStatus.COMPLETED);
            reservationRepo.save(r);

            String code = (r.getPromoCode() == null || r.getPromoCode().isBlank()) ? null : r.getPromoCode();
            BigDecimal total = paymentService
                    .calculateTotal(r, code)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal paidSoFar = paymentService.totalPaid(id);
            BigDecimal balance = total.subtract(paidSoFar).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                Payment p = paymentService.ensureBalance(id, balance);
                paymentService.markPaid(p.getId(), "CASH");
            }

            ra.addFlashAttribute("msg", "Reservation completed and payment marked PAID.");
            return "redirect:/reservations";
        }

        if (status != null) r.setStatus(status);
        reservationRepo.save(r);
        ra.addFlashAttribute("msg", "Reservation updated");
        return "redirect:/reservations";
    }

    // ---- helpers ----
    private static LocalDateTime parseLocal(String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); }
        catch (Exception ignore) {}
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); }
        catch (Exception ignore) {}
        try { return OffsetDateTime.parse(v).toLocalDateTime(); }   // <--- NEW
        catch (Exception ignore) {}
        try { return Instant.parse(v).atZone(ZoneId.systemDefault()).toLocalDateTime(); } // <--- NEW
        catch (Exception ignore) {}
        try { return LocalDateTime.parse(v); }
        catch (Exception ignore) { return null; }
    }

    private static boolean hasAnyRole(Authentication auth, String... roles) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (var ga : auth.getAuthorities()) {
            String got = ga.getAuthority();
            for (String want : roles) if (want.equals(got)) return true;
        }
        return false;
    }

    private static boolean isOwner(Authentication auth, Reservation r) {
        if (auth == null || r == null || r.getUser() == null || r.getUser().getEmail() == null) return false;
        return r.getUser().getEmail().equalsIgnoreCase(auth.getName());
    }
}
