package com.example.parking.controller;

import com.example.parking.dto.SummaryReservationDTO;
import com.example.parking.dto.SummaryStatus;
import com.example.parking.entity.Payment;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.enums.PaymentStatus;
import com.example.parking.entity.SummaryReservation;
import com.example.parking.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/reports")
public class ReportsController {

    private final LotRepository lotRepo;
    private final LevelRepository levelRepo;
    private final SlotRepository slotRepo;
    private final ReservationRepository reservationRepo;
    private final PaymentRepository paymentRepo;
    private final SummaryReservationRepository summaryRepo;

    public ReportsController(LotRepository lotRepo,
                             LevelRepository levelRepo,
                             SlotRepository slotRepo,
                             ReservationRepository reservationRepo,
                             PaymentRepository paymentRepo,
                             SummaryReservationRepository summaryRepo) {
        this.lotRepo = lotRepo;
        this.levelRepo = levelRepo;
        this.slotRepo = slotRepo;
        this.reservationRepo = reservationRepo;
        this.paymentRepo = paymentRepo;
        this.summaryRepo = summaryRepo;
    }

    @GetMapping
    public String page(Authentication auth, Model model,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String lot,
                       jakarta.servlet.http.HttpSession session) {

        final boolean isAdminOrManager = hasRole(auth, "ADMIN") || hasRole(auth, "MANAGER");
        final boolean isDriver = hasRole(auth, "DRIVER");
        final String myEmail = auth != null ? auth.getName() : null;

        // ======= SCOPE: reservations & payments =======
        final List<Reservation> scopedReservations =
                isAdminOrManager ? reservationRepo.findAll()
                        : safeFindReservationsForEmail(myEmail);

        final List<Payment> scopedPayments =
                isAdminOrManager ? paymentRepo.findAll()
                        : safeFindPaymentsForEmail(myEmail);

        // ------- Global counters (computed from scoped lists) -------
        long lotsCount = lotRepo.count();
        long levelsCount = levelRepo.count();
        long slotsCount = slotRepo.count();

        long reservationsCount = scopedReservations.size();
        long activeReservationsCount = scopedReservations.stream()
                .filter(r -> r.getStatus() != null && "ACTIVE".equals(r.getStatus().name()))
                .count();

        BigDecimal paidRevenue = scopedPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long refundPendingCount = scopedPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUND_PENDING)
                .count();

        List<Reservation> recentReservations = scopedReservations.stream()
                .sorted(Comparator.comparing(Reservation::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .toList();

        List<Payment> recentPayments = scopedPayments.stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .toList();

        // ------- Filters (safe defaults) -------
        LocalDate dFrom = parseDateOrDefault(from, LocalDate.now().minusDays(6));
        LocalDate dTo   = parseDateOrDefault(to,   LocalDate.now());
        if (dTo.isBefore(dFrom)) { LocalDate tmp = dFrom; dFrom = dTo; dTo = tmp; }
        LocalDateTime start = dFrom.atStartOfDay();
        LocalDateTime end   = dTo.atTime(23,59,59);
        String lotFilter = norm(lot);

        // Build lot options
        List<String> lots = collectLotsSafe();

        // ------- Filtered metrics from SCOPED lists -------
        List<Reservation> resInRange = scopedReservations.stream()
                .filter(r -> {
                    LocalDateTime st = r.getStartTime();
                    return st != null && !st.isBefore(start) && !st.isAfter(end);
                })
                .filter(r -> lotFilter == null || lotFilter.equals(norm(lotKey(r))))
                .toList();

        List<Payment> payInRange = scopedPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .filter(p -> {
                    LocalDateTime t = p.getCreatedAt();
                    return t != null && !t.isBefore(start) && !t.isAfter(end);
                })
                .filter(p -> {
                    if (lotFilter == null) return true;
                    Reservation rr = p.getReservation();
                    return rr != null && lotFilter.equals(norm(lotKey(rr)));
                })
                .toList();

        BigDecimal filteredRevenue = payInRange.stream()
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        int filteredReservationsCount = resInRange.size();
        int filteredPaymentsCount = payInRange.size();

        // Peak hours
        int[] peakHours = new int[24];
        for (Reservation r : resInRange) {
            LocalDateTime st = r.getStartTime();
            if (st != null) {
                int h = st.getHour();
                if (h >= 0 && h <= 23) peakHours[h]++;
            }
        }

        // Revenue by day
        List<DayRow> revenueByDay = new ArrayList<>();
        Map<LocalDate, BigDecimal> acc = new LinkedHashMap<>();
        for (LocalDate d = dFrom; !d.isAfter(dTo); d = d.plusDays(1)) {
            acc.put(d, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        for (Payment p : payInRange) {
            LocalDateTime t = p.getCreatedAt();
            if (t != null) {
                LocalDate key = t.toLocalDate();
                acc.computeIfPresent(key, (k, v) ->
                        v.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                                .setScale(2, RoundingMode.HALF_UP));
            }
        }
        for (Map.Entry<LocalDate, BigDecimal> e : acc.entrySet()) {
            revenueByDay.add(new DayRow(e.getKey().toString(), e.getValue()));
        }

        // ------- Model -------
        model.addAttribute("lotsCount", lotsCount);
        model.addAttribute("levelsCount", levelsCount);
        model.addAttribute("slotsCount", slotsCount);
        model.addAttribute("reservationsCount", reservationsCount);
        model.addAttribute("activeReservationsCount", activeReservationsCount);
        model.addAttribute("paidRevenue", paidRevenue);
        model.addAttribute("refundPendingCount", refundPendingCount);
        model.addAttribute("recentReservations", recentReservations);
        model.addAttribute("allReservations", scopedReservations);
        model.addAttribute("recentPayments", recentPayments);

        model.addAttribute("from", dFrom.toString());
        model.addAttribute("to", dTo.toString());
        model.addAttribute("lot", lot == null ? "" : lot);
        model.addAttribute("lots", lots);

        model.addAttribute("filteredRevenue", filteredRevenue);
        model.addAttribute("filteredReservationsCount", filteredReservationsCount);
        model.addAttribute("filteredPaymentsCount", filteredPaymentsCount);
        model.addAttribute("peakHours", peakHours);
        model.addAttribute("revenueByDay", revenueByDay);

        // Summary reservations (from DB)
        List<SummaryReservationDTO> summaries = summaryRepo.findAll().stream().map(ReportsController::toDto).toList();
        model.addAttribute("summaryReservations", summaries);

        // Visibility flags
        model.addAttribute("isAdmin", hasRole(auth, "ADMIN"));

        model.addAttribute("msg", null);
        model.addAttribute("err", null);
        return "reports";
    }

    @GetMapping("/summary")
    public String summaryPage(HttpSession session, Model model) {
        List<SummaryReservationDTO> list = summaryRepo.findAll().stream().map(ReportsController::toDto).toList();
        model.addAttribute("summaryReservations", list);
        return "reports-summary";
    }

    @GetMapping("/summary/edit")
    public String editSummaryPage(@RequestParam("id") Long id,
                                  HttpSession session,
                                  Model model) {
        List<SummaryReservationDTO> list = summaryRepo.findAll().stream().map(ReportsController::toDto).toList();
        SummaryReservation entity = summaryRepo.findById(id).orElse(null);
        if (entity == null) return "redirect:/reports/summary";
        model.addAttribute("summaryReservations", list);
        model.addAttribute("editId", id);
        model.addAttribute("editForm", toDto(entity));
        return "reports-summary";
    }

    @PostMapping("/summary")
    public String addSummary(@ModelAttribute SummaryReservationDTO form,
                             HttpSession session,
                             RedirectAttributes ra) {
        if (form.getStatus() == null) form.setStatus(SummaryStatus.ACTIVE);
        if (form.getDate() == null) form.setDate(LocalDate.now());

        // Auto-generate a sequential external ID (1,2,3,...) if not provided
        if (form.getId() == null) {
            Long next = summaryRepo.findAll().stream()
                    .map(SummaryReservation::getExternalId)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L) + 1L;
            form.setId(next);
        }

        SummaryReservation entity = toEntity(form);
        summaryRepo.save(entity);
        return "redirect:/reports/summary";
    }

    @PostMapping("/summary/delete")
    public String deleteSummary(@RequestParam("id") Long id,
                                @RequestParam(value = "from", required = false) String from,
                                HttpSession session,
                                RedirectAttributes ra) {
        if (id != null) summaryRepo.deleteById(id);
        return "main".equalsIgnoreCase(from) ? "redirect:/reports" : "redirect:/reports/summary";
    }

    @PostMapping("/summary/edit")
    public String saveEditSummary(@RequestParam("id") Long id,
                                  @ModelAttribute SummaryReservationDTO form,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (id != null) {
            SummaryReservation existing = summaryRepo.findById(id).orElse(null);
            if (existing != null) {
                // Keep existing external ID; do not allow editing it
                existing.setUser(form.getUser());
                existing.setHours(form.getHours());
                existing.setStatus(form.getStatus() == null ? SummaryStatus.ACTIVE : form.getStatus());
                existing.setDate(form.getDate() == null ? LocalDate.now() : form.getDate());
                existing.setAmount(form.getAmount());
                summaryRepo.save(existing);
            }
        }
        return "redirect:/reports/summary";
    }

    private static SummaryReservationDTO toDto(SummaryReservation e){
        SummaryReservationDTO d = new SummaryReservationDTO();
        d.setDbId(e.getId());
        d.setId(e.getExternalId());
        d.setUser(e.getUser());
        d.setHours(e.getHours());
        d.setStatus(e.getStatus());
        d.setDate(e.getDate());
        d.setAmount(e.getAmount());
        return d;
    }
    private static SummaryReservation toEntity(SummaryReservationDTO d){
        SummaryReservation e = new SummaryReservation();
        e.setExternalId(d.getId());
        e.setUser(d.getUser());
        e.setHours(d.getHours());
        e.setStatus(d.getStatus());
        e.setDate(d.getDate());
        e.setAmount(d.getAmount());
        return e;
    }

    /* ---------------- helpers ---------------- */

    private static boolean hasRole(Authentication auth, String role){
        if (auth == null) return false;
        String want1 = "ROLE_" + role.toUpperCase(Locale.ROOT);
        String want2 = role.toUpperCase(Locale.ROOT);
        for (GrantedAuthority a : auth.getAuthorities()){
            String got = String.valueOf(a.getAuthority());
            if (want1.equalsIgnoreCase(got) || want2.equalsIgnoreCase(got)) return true;
        }
        return false;
    }

    private static LocalDate parseDateOrDefault(String s, LocalDate def) {
        if (s == null || s.isBlank()) return def;
        try { return LocalDate.parse(s); } catch (Exception e) { return def; }
    }
    private static String norm(String s){ return s == null ? null : s.trim().toUpperCase(Locale.ROOT); }

    /** Best-effort lot label from Reservation→Slot→Lot. */
    private static String lotKey(Reservation r){
        try {
            Object slot = call(r, "getSlot");
            if (slot == null) return null;
            String direct = readString(slot, "getLotName", "getLot");
            if (direct != null && !direct.isBlank()) return direct;
            Object lotObj = call(slot, "getLot");
            String lotName = readString(lotObj, "getName", "getCode");
            if (lotName != null && !lotName.isBlank()) return lotName;
            Object lotId = call(slot, "getLotId");
            if (lotId != null) return "LOT-" + String.valueOf(lotId);
        } catch (Exception ignored) {}
        return null;
    }

    /** Try repo methods by email; otherwise in-memory filter — no signature changes required. */
    private List<Reservation> safeFindReservationsForEmail(String email) {
        if (email == null || email.isBlank()) return Collections.emptyList();
        // try: findAllByUserEmail, findByUserEmail
        for (String m : List.of("findAllByUserEmail", "findByUserEmail")) {
            try {
                Method mm = reservationRepo.getClass().getMethod(m, String.class);
                Object out = mm.invoke(reservationRepo, email);
                if (out instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Reservation> cast = (List<Reservation>) list;
                    return cast;
                }
            } catch (NoSuchMethodException ignore) { /* fall through */ }
            catch (Exception e) { /* fall through to memory filter */ }
        }
        // fallback: filter all
        return reservationRepo.findAll().stream()
                .filter(r -> {
                    try {
                        return r.getUser()!=null && r.getUser().getEmail()!=null
                                && r.getUser().getEmail().equalsIgnoreCase(email);
                    } catch (Exception ex) { return false; }
                })
                .collect(Collectors.toList());
    }

    private List<Payment> safeFindPaymentsForEmail(String email) {
        if (email == null || email.isBlank()) return Collections.emptyList();
        // try: findAllByUserEmail on paymentRepo
        for (String m : List.of("findAllByUserEmail")) {
            try {
                Method mm = paymentRepo.getClass().getMethod(m, String.class);
                Object out = mm.invoke(paymentRepo, email);
                if (out instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Payment> cast = (List<Payment>) list;
                    return cast;
                }
            } catch (NoSuchMethodException ignore) { /* fall through */ }
            catch (Exception e) { /* fall through to memory filter */ }
        }
        // fallback: filter by reservation.user.email
        return paymentRepo.findAll().stream()
                .filter(p -> {
                    try {
                        return p.getReservation()!=null
                                && p.getReservation().getUser()!=null
                                && p.getReservation().getUser().getEmail()!=null
                                && p.getReservation().getUser().getEmail().equalsIgnoreCase(email);
                    } catch (Exception ex) { return false; }
                })
                .collect(Collectors.toList());
    }

    /** Build lot select options safely (from lots table if possible; otherwise from reservations). */
    private List<String> collectLotsSafe() {
        try {
            List<?> lots = (List<?>) lotRepo.getClass().getMethod("findAll").invoke(lotRepo);
            Set<String> names = new TreeSet<>();
            for (Object l : lots) {
                String n = readString(l, "getName", "getCode", "getTitle");
                if (n == null || n.isBlank()) {
                    Object id = call(l, "getId");
                    n = id == null ? null : ("LOT-" + id);
                }
                if (n != null && !n.isBlank()) names.add(n);
            }
            if (!names.isEmpty()) return new ArrayList<>(names);
        } catch (Exception ignored) { /* fall back */ }

        Set<String> names = new TreeSet<>();
        for (Reservation r : reservationRepo.findAll()) {
            String k = lotKey(r);
            if (k != null && !k.isBlank()) names.add(k);
        }
        return new ArrayList<>(names);
    }

    private static Object call(Object obj, String... getters) {
        if (obj == null) return null;
        for (String g : getters) {
            try { Method m = obj.getClass().getMethod(g); return m.invoke(obj); }
            catch (Exception ignored) {}
        }
        return null;
    }
    private static String readString(Object obj, String... getters){
        Object v = call(obj, getters);
        return v == null ? null : String.valueOf(v);
    }

    /* DTO */
    public static class DayRow {
        private final String date;
        private final BigDecimal revenue;
        public DayRow(String date, BigDecimal revenue) { this.date = date; this.revenue = revenue; }
        public String getDate() { return date; }
        public BigDecimal getRevenue() { return revenue; }
    }
}
