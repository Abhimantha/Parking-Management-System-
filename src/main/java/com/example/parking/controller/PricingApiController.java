package com.example.parking.controller;

import com.example.parking.entity.Reservation;
import com.example.parking.entity.Slot;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.repository.SlotRepository;
import com.example.parking.service.PricingEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Pricing API
 * - /api/pricing/quote                  : quote by slot + times (supports promo)
 * - /api/pricing/quote/reservation      : quote by reservationId (supports promo)
 * - /api/pricing/checkout-amount        : computes amount due for a reservation & phase (ADVANCE/FULL)
 *
 * Notes:
 * • Parsing is robust: accepts ISO with/without zone, HTML datetime-local, and plain date.
 * • All responses are pure JSON and do not alter other subsystems.
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingApiController {

    private final PricingEngineService engine;
    private final SlotRepository slots;
    private final ReservationRepository reservations;

    public PricingApiController(PricingEngineService engine,
                                SlotRepository slots,
                                ReservationRepository reservations) {
        this.engine = engine;
        this.slots = slots;
        this.reservations = reservations;
    }

    /* ================================================================
       1) Quote by slot + explicit times (kept compatible with your UI)
       ================================================================ */
    @GetMapping("/quote")
    public ResponseEntity<?> quote(@RequestParam Long slotId,
                                   @RequestParam String start,
                                   @RequestParam String end,
                                   @RequestParam(required = false) String promo) {
        Slot s = slots.findById(slotId).orElse(null);
        if (s == null) {
            return badRequest("Slot not found");
        }

        LocalDateTime st = parseLdt(start);
        LocalDateTime en = parseLdt(end);
        if (st == null || en == null) {
            return badRequest("Invalid date/time format. Use ISO or 'yyyy-MM-dd''T''HH:mm'.");
        }
        if (!en.isAfter(st)) {
            return badRequest("End must be after start.");
        }

        var q = (promo == null || promo.isBlank())
                ? engine.quote(s, st, en)
                : engine.quote(s, st, en, promo.trim());

        return ResponseEntity.ok(q);
    }

    /* ================================================================
       2) Quote by reservation (for checkout screens or server flows)
       ================================================================ */
    @GetMapping("/quote/reservation")
    public ResponseEntity<?> quoteByReservation(@RequestParam Long reservationId,
                                                @RequestParam(required = false) String promo) {
        Reservation r = reservations.findById(reservationId).orElse(null);
        if (r == null) return notFound("Reservation not found");

        Slot s = r.getSlot();
        if (s == null || r.getStartTime() == null || r.getEndTime() == null) {
            return badRequest("Reservation is missing slot or time window.");
        }

        var q = (promo == null || promo.isBlank())
                ? engine.quote(s, r.getStartTime(), r.getEndTime())
                : engine.quote(s, r.getStartTime(), r.getEndTime(), promo.trim());

        // Also echo context so callers can confirm inputs
        return ResponseEntity.ok(Map.of(
                "reservationId", reservationId,
                "slotId", s.getId(),
                "start", r.getStartTime(),
                "end", r.getEndTime(),
                "quote", q
        ));
    }

    /* ================================================================
       3) Amount for checkout (reservation + ADVANCE/FULL)
       - factor: ADVANCE=0.30 (30%), FULL=1.00
       ================================================================ */
    @GetMapping("/checkout-amount")
    public ResponseEntity<?> checkoutAmount(@RequestParam Long reservationId,
                                            @RequestParam(required = false) String promo,
                                            @RequestParam(required = false, defaultValue = "ADVANCE") String phase) {
        Reservation r = reservations.findById(reservationId).orElse(null);
        if (r == null) return notFound("Reservation not found");
        Slot s = r.getSlot();
        if (s == null) return badRequest("Reservation has no slot.");

        var q = (promo == null || promo.isBlank())
                ? engine.quote(s, r.getStartTime(), r.getEndTime())
                : engine.quote(s, r.getStartTime(), r.getEndTime(), promo.trim());

        BigDecimal factor = "FULL".equalsIgnoreCase(phase) ? BigDecimal.ONE : new BigDecimal("0.30");
        BigDecimal amountDue = q.total.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(Map.of(
                "reservationId", reservationId,
                "phase", phase.toUpperCase(),
                "base", q.base,
                "total", q.total,
                "adjustments", q.adjustments,
                "factor", factor,
                "amountDue", amountDue
        ));
    }

    /* =================== helpers =================== */

    private static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }

    private static ResponseEntity<Map<String, Object>> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", msg));
    }

    /**
     * Flexible parser:
     *  - OffsetDateTime (e.g., 2025-09-26T11:45:00+05:30 or Z)
     *  - LocalDateTime (e.g., 2025-09-26T11:45:00)
     *  - HTML datetime-local (yyyy-MM-dd'T'HH:mm)
     *  - "yyyy-MM-dd HH:mm"
     *  - "yyyy-MM-dd" (as start-of-day)
     *  - Fallback: strip trailing Z or zone and try LocalDateTime
     */
    private static LocalDateTime parseLdt(String v) {
        if (v == null || v.isBlank()) return null;

        // Normalize whitespace
        v = v.trim();

        // Try OffsetDateTime
        try { return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignore) {}

        // Try LocalDateTime ISO
        try { return LocalDateTime.parse(v); } catch (Exception ignore) {}

        // HTML datetime-local
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); } catch (Exception ignore) {}

        // Common space-separated pattern
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); } catch (Exception ignore) {}

        // Date only
        try { return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(); } catch (Exception ignore) {}

        // Instant (Z)
        try { return Instant.parse(v).atZone(ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignore) {}

        // Final fallback: strip trailing 'Z' or timezone and parse
        String cleaned = v.replaceAll("Z$", "").replaceAll("[+-]\\d\\d:?\\d\\d$", "");
        try { return LocalDateTime.parse(cleaned); } catch (Exception ignore) {}

        return null;
    }
}
