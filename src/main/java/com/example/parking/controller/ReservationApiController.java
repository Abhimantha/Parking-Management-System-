package com.example.parking.controller;

import com.example.parking.entity.Reservation;
import com.example.parking.entity.enums.ReservationStatus;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationApiController {

    private final ReservationService service;
    private final ReservationRepository repo;

    public ReservationApiController(ReservationService service, ReservationRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @GetMapping
    public List<Reservation> list(Authentication auth) {
        boolean adminOrMgr = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        if (adminOrMgr) return repo.findAll();
        if (auth == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return repo.findByUserEmailOrderByStartTimeDesc(auth.getName());
    }

    @PostMapping
    public Reservation create(@RequestParam Long userId,
                              @RequestParam Long slotId,
                              @RequestBody ReservationPayload body) {
        Reservation r = new Reservation();
        r.setStartTime(parseLdt(body.startTime));
        r.setEndTime(parseLdt(body.endTime));
        r.setStatus(parseStatus(body.status, ReservationStatus.ACTIVE));
        if (body.promoCode != null && !body.promoCode.isBlank())
            r.setPromoCode(body.promoCode.trim());
        return service.create(r, userId, slotId);
    }

    @PutMapping("/{id}")
    public Reservation update(@PathVariable Long id,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) Long slotId,
                              @RequestBody ReservationPayload body) {
        Reservation r = new Reservation();
        if (body.startTime != null) r.setStartTime(parseLdt(body.startTime));
        if (body.endTime != null)   r.setEndTime(parseLdt(body.endTime));
        if (body.status != null)    r.setStatus(parseStatus(body.status, null));
        if (body.promoCode != null) r.setPromoCode(body.promoCode == null ? null : body.promoCode.trim());
        return service.update(id, r, userId, slotId);
    }

    @PatchMapping("/{id}/cancel")
    public Reservation cancel(@PathVariable Long id, @RequestBody CancelPayload body) {
        Reservation r = service.findById(id);
        if (body != null && body.reason != null && !body.reason.isBlank()) {
            r.setCancelReason(body.reason);
            r.setCanceledAt(LocalDateTime.now());
        }
        r.setStatus(ReservationStatus.CANCELLED);
        return repo.save(r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        if (reason != null && !reason.isBlank()) {
            Reservation r = service.findById(id);
            r.setCancelReason(reason);
            r.setCanceledAt(LocalDateTime.now());
            repo.save(r);
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /* --------- payloads --------- */
    public static class ReservationPayload {
        public String startTime;
        public String endTime;
        public String status;
        public String promoCode;   // <--- NEW
    }
    public static class CancelPayload { public String reason; }

    /* --------- helpers --------- */
    private static LocalDateTime parseLdt(String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDateTime.parse(v); } catch (Exception ignore) {}
        try { return OffsetDateTime.parse(v).toLocalDateTime(); } catch (Exception ignore) {}
        try { return Instant.parse(v).atZone(ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignore) {}
        String[] pats = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm"};
        for (String p : pats) try {
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern(p));
        } catch (Exception ignore) {}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad datetime: " + v);
    }
    private static ReservationStatus parseStatus(String s, ReservationStatus def) {
        if (s == null || s.isBlank()) return def;
        try { return ReservationStatus.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad status: " + s); }
    }
}
