package com.example.parking.controller;

import com.example.parking.entity.Level;
import com.example.parking.entity.Lot;
import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.SlotStatus;
import com.example.parking.repository.LevelRepository;
import com.example.parking.repository.LotRepository;
import com.example.parking.repository.SlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin/Manager-only CRUD for Lots + creating Slots inside a Lot (with type & priceRate).
 * Path is /api/lotmgmt to avoid colliding with any existing endpoints.
 */
@RestController
@RequestMapping("/api/lotmgmt")
public class LotManagementApiController {

    private final LotRepository lots;
    private final SlotRepository slots;
    private final LevelRepository levels;

    public LotManagementApiController(LotRepository lots, SlotRepository slots, LevelRepository levels) {
        this.lots = lots;
        this.slots = slots;
        this.levels = levels;
    }

    // -------- LOTS --------

    @GetMapping("/lots")
    public List<Map<String, Object>> listLots() {
        // Build counts by Lot ID safely
        Map<Long, Long> counts = slots.findAll().stream()
                .map(LotManagementApiController::slotLotId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return lots.findAll().stream().map(lotObj -> {
            Long id = getLong(lotObj, "getId");
            String name = safe(getString(lotObj, "getName", "toString"));
            String location = safe(getString(lotObj, "getLocation", "getAddress", "toString"));
            Integer totalCapacity = getInt(lotObj, "getTotalCapacity", "getCapacity");

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("location", location);
            m.put("totalCapacity", totalCapacity);
            m.put("slotsCount", counts.getOrDefault(id, 0L));
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/lots")
    public ResponseEntity<?> createLot(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String location = String.valueOf(body.getOrDefault("location", "")).trim();
        int totalCapacity = parseInt(body.get("totalCapacity"), 0);

        if (name.isBlank()) return bad("Name is required");
        if (totalCapacity < 0) return bad("Total capacity must be >= 0");

        Lot l = new Lot();
        setIfPresent(l, "setName", name);
        setIfPresent(l, "setLocation", location);
        setIfPresent(l, "setTotalCapacity", totalCapacity);
        lots.save(l);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", l.getId());
        resp.put("name", safe(name));
        resp.put("location", safe(location));
        resp.put("totalCapacity", totalCapacity);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/lots/{id}")
    public ResponseEntity<?> updateLot(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Lot> opt = lots.findById(id);
        if (opt.isEmpty()) return notFound("Lot not found: " + id);
        Lot l = opt.get();

        if (body.containsKey("name")) {
            String v = String.valueOf(body.get("name")).trim();
            if (v.isBlank()) return bad("Name cannot be blank");
            setIfPresent(l, "setName", v);
        }
        if (body.containsKey("location")) {
            setIfPresent(l, "setLocation", String.valueOf(body.get("location")).trim());
        }
        if (body.containsKey("totalCapacity")) {
            int cap = parseInt(body.get("totalCapacity"), 0);
            if (cap < 0) return bad("Total capacity must be >= 0");

            long existing = slots.findAll().stream()
                    .map(LotManagementApiController::slotLotId)
                    .filter(Objects::nonNull)
                    .filter(lotId -> lotId.equals(id))
                    .count();
            if (cap < existing) return bad("Capacity cannot be less than existing slot count (" + existing + ")");
            setIfPresent(l, "setTotalCapacity", cap);
        }
        lots.save(l);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/lots/{id}")
    public ResponseEntity<?> deleteLot(@PathVariable Long id) {
        Optional<Lot> opt = lots.findById(id);
        if (opt.isEmpty()) return notFound("Lot not found: " + id);

        boolean hasSlots = slots.findAll().stream()
                .map(LotManagementApiController::slotLotId)
                .anyMatch(lotId -> Objects.equals(lotId, id));
        if (hasSlots) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Lot has slots. Delete/move those first."));
        }
        lots.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // -------- LEVELS (optional helper for the UI) --------

    @GetMapping("/lots/{lotId}/levels")
    public List<Map<String, Object>> levelsForLot(@PathVariable Long lotId) {
        return levels.findAll().stream()
                .filter(lv -> Objects.equals(getLongSafe(getObject(lv, "getLot"), "getId"), lotId))
                .map(lv -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", getLong(lv, "getId"));
                    m.put("name", safe(getString(lv, "getName", "getLabel", "getCode", "toString")));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // -------- SLOTS inside a Lot --------

    @GetMapping("/lots/{lotId}/slots")
    public List<Map<String, Object>> slotsInLot(@PathVariable Long lotId) {
        return slots.findAll().stream()
                .filter(s -> Objects.equals(slotLotId(s), lotId))
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("slotNumber", safe(getString(s, "getSlotNumber", "getCode", "getLabel", "toString")));
                    m.put("type", safe(getString(s, "getType", "toString")));
                    m.put("status", String.valueOf(getString(s, "getStatus", "toString")));
                    m.put("priceRate", s.getPriceRate() == null ? "0.00" : s.getPriceRate().toPlainString());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/lots/{lotId}/slots")
    public ResponseEntity<?> createSlotInLot(@PathVariable Long lotId, @RequestBody Map<String, Object> body) {
        Optional<Lot> opt = lots.findById(lotId);
        if (opt.isEmpty()) return notFound("Lot not found: " + lotId);
        Lot lot = opt.get();

        // capacity check
        long existing = slots.findAll().stream()
                .filter(s -> Objects.equals(slotLotId(s), lotId))
                .count();
        int cap = getInt(lot, "getTotalCapacity", "getCapacity");
        if (cap > 0 && existing >= cap) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Lot is at capacity (" + cap + ")"));
        }

        String slotNumber = String.valueOf(body.getOrDefault("slotNumber", "")).trim();
        String type = String.valueOf(body.getOrDefault("type", "COMPACT")).trim();
        BigDecimal priceRate = parseMoney(body.get("priceRate"));

        if (slotNumber.isBlank()) return bad("slotNumber is required");
        if (priceRate.compareTo(BigDecimal.ZERO) < 0) return bad("priceRate must be >= 0");

        Slot s = new Slot();
        setIfPresent(s, "setSlotNumber", slotNumber);
        setIfPresent(s, "setType", type);
        setIfPresent(s, "setStatus", SlotStatus.AVAILABLE);
        s.setPriceRate(priceRate);
        setIfPresent(s, "setLot", lot);

        Long levelId = body.get("levelId") == null ? null : parseLong(body.get("levelId"));
        if (levelId != null) {
            levels.findById(levelId).ifPresent(lv -> setIfPresent(s, "setLevel", lv));
        }
        slots.save(s);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", s.getId());
        resp.put("slotNumber", slotNumber);
        resp.put("type", type);
        resp.put("priceRate", s.getPriceRate().toPlainString());
        return ResponseEntity.ok(resp);
    }

    // ---------- helpers ----------

    private static String safe(String s) { return s == null ? "" : s; }

    private static int parseInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private static Long parseLong(Object o) {
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static BigDecimal parseMoney(Object o) {
        try { return new BigDecimal(String.valueOf(o)).setScale(2); }
        catch (Exception e) { return new BigDecimal("0.00"); }
    }

    private static String getString(Object obj, String... getters) {
        if (obj == null) return null;
        for (String g : getters) {
            try { return String.valueOf(obj.getClass().getMethod(g).invoke(obj)); } catch (Exception ignored) {}
        }
        return null;
    }
    private static Long getLong(Object obj, String... getters) {
        if (obj == null) return null;
        for (String g : getters) {
            try {
                Object v = obj.getClass().getMethod(g).invoke(obj);
                if (v == null) return null;
                if (v instanceof Number n) return n.longValue();
                return Long.parseLong(String.valueOf(v));
            } catch (Exception ignored) {}
        }
        return null;
    }
    private static Long getLongSafe(Object obj, String getter) {
        return getLong(obj, getter);
    }
    private static Integer getInt(Object obj, String... getters) {
        if (obj == null) return 0;
        for (String g : getters) {
            try {
                Object v = obj.getClass().getMethod(g).invoke(obj);
                if (v == null) return 0;
                if (v instanceof Number n) return n.intValue();
                return Integer.parseInt(String.valueOf(v));
            } catch (Exception ignored) {}
        }
        return 0;
    }
    private static Object getObject(Object obj, String getter) {
        if (obj == null) return null;
        try { return obj.getClass().getMethod(getter).invoke(obj); } catch (Exception e) { return null; }
    }
    private static <T> void setIfPresent(Object obj, String setter, T value) {
        if (obj == null) return;
        try {
            obj.getClass().getMethod(setter, value.getClass()).invoke(obj, value);
        } catch (NoSuchMethodException e1) {
            // try primitives for common types
            try {
                if (value instanceof Integer i)
                    obj.getClass().getMethod(setter, int.class).invoke(obj, i.intValue());
                else if (value instanceof Long l)
                    obj.getClass().getMethod(setter, long.class).invoke(obj, l.longValue());
                else
                    throw e1;
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static Long slotLotId(Slot s) {
        try {
            Object lot = s.getClass().getMethod("getLot").invoke(s);
            return getLong(lot, "getId");
        } catch (Exception e) {
            return null;
        }
    }

    private static ResponseEntity<Map<String, String>> bad(String msg) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", msg);
        return ResponseEntity.badRequest().body(m);
    }
    private static ResponseEntity<Map<String, String>> notFound(String msg) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", msg);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(m);
    }
}
