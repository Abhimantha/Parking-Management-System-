package com.example.parking.controller;

import com.example.parking.entity.Level;
import com.example.parking.entity.Lot;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.Slot;
import com.example.parking.repository.LevelRepository;
import com.example.parking.repository.LotRepository;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.repository.SlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AvailabilityApiController {

    private final SlotRepository slotRepository;
    private final LotRepository lotRepository;
    private final LevelRepository levelRepository;
    private final ReservationRepository reservationRepository;

    public AvailabilityApiController(SlotRepository slotRepository,
                                     LotRepository lotRepository,
                                     LevelRepository levelRepository,
                                     ReservationRepository reservationRepository) {
        this.slotRepository = slotRepository;
        this.lotRepository = lotRepository;
        this.levelRepository = levelRepository;
        this.reservationRepository = reservationRepository;
    }

    // ---------- Dropdown data ----------

    @GetMapping("/lots")
    public List<Map<String, Object>> lots() {
        return lotRepository.findAll().stream()
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", getLong(l, "getId"));
                    m.put("name", getString(l, "getName", "getLabel", "toString"));
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/levels")
    public List<Map<String, Object>> levels(@RequestParam(required = false) Long lotId) {
        List<Level> levels = levelRepository.findAll();

        if (lotId != null) {
            final Long selectedLotId = lotId;
            levels = levels.stream().filter(lv -> {
                Object lotObj = callGetter(lv, "getLot");
                Long lvLotId = getLong(lotObj, "getId");
                if (lvLotId != null) return Objects.equals(lvLotId, selectedLotId);

                // fallback by name if only names exist
                String lvLotName = getString(lotObj, "getName", "getLabel", "toString");
                String selectedLotName = lotRepository.findById(selectedLotId)
                        .map(l -> getString(l, "getName", "getLabel", "toString"))
                        .orElse(null);
                return selectedLotName != null && selectedLotName.equalsIgnoreCase(nvl(lvLotName));
            }).collect(Collectors.toList());
        }

        return levels.stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", getLong(v, "getId"));
                    m.put("name", getString(v, "getName", "getLabel", "getCode", "toString"));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ---------- Availability ----------

    @GetMapping("/availability")
    public List<Map<String, Object>> availability(
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false, name = "start") String startAlias,
            @RequestParam(required = false) String endAt,
            @RequestParam(required = false, name = "end") String endAlias
    ) {
        Instant start = parseToInstant(firstNonBlank(startAt, startAlias));
        Instant end   = parseToInstant(firstNonBlank(endAt, endAlias));

        if (start == null) start = Instant.now();
        if (end == null) end = start.plusSeconds(2 * 60 * 60); // +2h
        if (!end.isAfter(start)) end = start.plusSeconds(30 * 60); // min window 30m

        final Instant s = start;
        final Instant e = end;
        final Long selLotId = lotId;
        final Long selLevelId = levelId;

        // Start from all slots and filter by lot/level if provided
        List<Slot> slots = slotRepository.findAll();
        if (selLotId != null) {
            slots = slots.stream().filter(slt -> {
                Object lotObj = callGetter(slt, "getLot");
                Long sLotId = getLong(lotObj, "getId");
                if (sLotId != null) return Objects.equals(sLotId, selLotId);

                String lotName = getString(lotObj, "getName", "getLabel", "toString");
                String selName = lotRepository.findById(selLotId)
                        .map(x -> getString(x, "getName", "getLabel", "toString"))
                        .orElse(null);
                return selName != null && selName.equalsIgnoreCase(nvl(lotName));
            }).collect(Collectors.toList());
        }
        if (selLevelId != null) {
            slots = slots.stream().filter(slt -> {
                Object lvObj = callGetter(slt, "getLevel");
                Long sLevelId = getLong(lvObj, "getId");
                if (sLevelId != null) return Objects.equals(sLevelId, selLevelId);

                String levelName = getString(lvObj, "getName", "getLabel", "getCode", "toString");
                String selName = levelRepository.findById(selLevelId)
                        .map(x -> getString(x, "getName", "getLabel", "getCode", "toString"))
                        .orElse(null);
                return selName != null && selName.equalsIgnoreCase(nvl(levelName));
            }).collect(Collectors.toList());
        }

        // Compute occupied slot IDs (overlap rule: r.start < e && r.end > s)
        // Optionally ignore CANCELLED/EXPIRED
        Set<Long> occupied = reservationRepository.findAll().stream()
                .filter(r -> r.getSlot() != null && r.getStartTime() != null && r.getEndTime() != null)
                .filter(r -> {
                    String st = String.valueOf(r.getStatus());
                    if ("CANCELLED".equalsIgnoreCase(st) || "EXPIRED".equalsIgnoreCase(st)) return false;
                    return true;
                })
                .filter(r ->
                        r.getStartTime().atZone(ZoneId.systemDefault()).toInstant().isBefore(e) &&
                                r.getEndTime().atZone(ZoneId.systemDefault()).toInstant().isAfter(s)
                )
                .map(r -> getLong(r.getSlot(), "getId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Available = all minus occupied
        List<Slot> available = slots.stream()
                .filter(slt -> !occupied.contains(getLong(slt, "getId")))
                .collect(Collectors.toList());

        return available.stream().map(slt -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Long id = getLong(slt, "getId");
            String code = nvl(getString(slt, "getLabel", "getCode", "getName", "getNumber"), "Slot #" + id);
            Object lotObj = callGetter(slt, "getLot");
            Object lvObj = callGetter(slt, "getLevel");

            m.put("id", id);
            m.put("code", code);
            m.put("lotId", getLong(lotObj, "getId"));
            m.put("lotName", getString(lotObj, "getName", "getLabel", "toString"));
            m.put("levelId", getLong(lvObj, "getId"));
            m.put("levelName", getString(lvObj, "getName", "getLabel", "getCode", "toString"));
            m.put("status", "AVAILABLE");
            m.put("startAt", s.toString());
            m.put("endAt", e.toString());
            return m;
        }).collect(Collectors.toList());
    }

    // ---------- helpers ----------

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static Object callGetter(Object obj, String... methodNames) {
        if (obj == null) return null;
        for (String m : methodNames) {
            try {
                Method method = obj.getClass().getMethod(m);
                return method.invoke(obj);
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static Long getLong(Object obj, String... getterNames) {
        Object v = callGetter(obj, getterNames);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e1) { return null; }
    }

    private static String getString(Object obj, String... getterNames) {
        Object v = callGetter(obj, getterNames);
        return (v == null) ? null : String.valueOf(v);
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    private static String nvl(String s, String d) { return (s == null || s.isBlank()) ? d : s; }

    /**
     * Accepts:
     *  - ISO instants (e.g. 2025-09-22T10:00:00Z) and offset datetimes
     *  - Local datetimes with/without seconds: "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
     *    plus space/comma variants, and date-only "yyyy-MM-dd" (start of day, system zone).
     */
    private static Instant parseToInstant(String v) {
        if (v == null || v.isBlank()) return null;

        // ISO instant / offset
        try { return Instant.parse(v); } catch (Exception ignore) {}
        try { return OffsetDateTime.parse(v).toInstant(); } catch (Exception ignore) {}

        // Common local formats (system default zone)
        String[] pats = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd, HH:mm",
                "yyyy-MM-dd"
        };
        for (String p : pats) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p);
                if (p.equals("yyyy-MM-dd")) {
                    LocalDate d = LocalDate.parse(v, fmt);
                    return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
                } else {
                    LocalDateTime dt = LocalDateTime.parse(v, fmt);
                    return dt.atZone(ZoneId.systemDefault()).toInstant();
                }
            } catch (Exception ignore) {}
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized datetime: " + v);
    }
}
