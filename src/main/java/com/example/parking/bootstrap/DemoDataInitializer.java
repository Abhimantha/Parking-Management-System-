package com.example.parking.bootstrap;

import com.example.parking.entity.Level;
import com.example.parking.entity.Lot;
import com.example.parking.entity.Slot;
import com.example.parking.repository.LevelRepository;
import com.example.parking.repository.LotRepository;
import com.example.parking.repository.SlotRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final LotRepository lotRepo;
    private final LevelRepository levelRepo;
    private final SlotRepository slotRepo;

    public DemoDataInitializer(LotRepository lotRepo, LevelRepository levelRepo, SlotRepository slotRepo) {
        this.lotRepo = lotRepo;
        this.levelRepo = levelRepo;
        this.slotRepo = slotRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedLot("Lot A");
        seedLot("Lot B");
        seedLot("Lot C");
    }

    private void seedLot(String lotName) {
        Lot lot = findLotByName(lotName);
        if (lot == null) {
            lot = new Lot();
            setIfExists(lot, "setName", String.class, lotName);
            setIfExists(lot, "setCode", String.class, lotName);
            lot = lotRepo.save(lot);
        }

        for (int i = 1; i <= 3; i++) {
            String levelName = "L" + i;
            Level level = findLevel(lot, levelName);
            if (level == null) {
                level = new Level();
                setIfExists(level, "setName", String.class, levelName);
                setIfExists(level, "setCode", String.class, levelName);
                setIfExists(level, "setLot", Lot.class, lot);
                level = levelRepo.save(level);
            }
            ensureSlots(level, lot, 10); // 10 slots/level
        }
    }

    private void ensureSlots(Level level, Lot lot, int desiredCount) {
        String levelName = getString(level, "getName", "getCode", "toString");
        String lotName = getString(lot, "getName", "getCode", "toString");

        // Existing slots for this (lot, level) for idempotency
        List<Slot> existingForLevel = slotRepo.findAll().stream()
                .filter(s -> {
                    String slLevel = getString(s, "getLevel", "getLevelName", "getLevelCode");
                    String slLot   = getString(s, "getLot", "getLotName");
                    return eq(levelName, slLevel) && eq(lotName, slLot);
                })
                .collect(Collectors.toList());

        int toCreate  = Math.max(0, desiredCount - existingForLevel.size());
        int startIdx  = existingForLevel.size() + 1;

        for (int k = 0; k < toCreate; k++) {
            int n = startIdx + k;

            // Make slot_number globally unique to satisfy unique index on slot_number
            String slotNumber = lotName + "-" + levelName + "-S" + String.format("%02d", n);
            String label      = slotNumber;

            // simple grid (5 columns)
            int colIndex = (n - 1) % 5;
            int rowIndex = (n - 1) / 5;

            Slot slot = new Slot();

            // ---- REQUIRED string fields commonly annotated with @NotBlank ----
            // Level as STRING (if mapped)
            setIfExists(slot, "setLevel", String.class, levelName);
            setIfExists(slot, "setLevelName", String.class, levelName);
            setIfExists(slot, "setLevelCode", String.class, levelName);

            // Lot as STRING (if mapped)
            setIfExists(slot, "setLot", String.class, lotName);
            setIfExists(slot, "setLotName", String.class, lotName);

            // Slot number as STRING (globally unique now)
            setIfExists(slot, "setSlotNumber", String.class, slotNumber);
            setIfExists(slot, "setSlot_number", String.class, slotNumber);

            // ---- OPTIONAL relation fields if your entity uses them ----
            setIfExists(slot, "setLevel", Level.class, level);
            setIfExists(slot, "setLot", Lot.class, lot);

            // Labels / misc
            setIfExists(slot, "setLabel", String.class, label);
            setIfExists(slot, "setCode", String.class, label);
            setIfExists(slot, "setName", String.class, label);
            setIfExists(slot, "setNumber", Integer.class, n);
            setIfExists(slot, "setStatus", String.class, "AVAILABLE");
            setIfExists(slot, "setType", String.class, "CAR");

            // Grid/position fields covering common names
            setIfExists(slot, "setMapCol", Integer.class, colIndex);
            setIfExists(slot, "setMap_col", Integer.class, colIndex);
            setIfExists(slot, "setMapRow", Integer.class, rowIndex);
            setIfExists(slot, "setMap_row", Integer.class, rowIndex);

            setIfExists(slot, "setColIndex", Integer.class, colIndex);
            setIfExists(slot, "setCol_index", Integer.class, colIndex);
            setIfExists(slot, "setColumnIndex", Integer.class, colIndex);

            setIfExists(slot, "setRowIndex", Integer.class, rowIndex);
            setIfExists(slot, "setRow_index", Integer.class, rowIndex);
            setIfExists(slot, "setLineIndex", Integer.class, rowIndex);

            slotRepo.save(slot);
        }
    }

    // ---------- helpers ----------
    private static boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static Object callGetter(Object obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            try {
                Method m = obj.getClass().getMethod(n);
                return m.invoke(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Long getLong(Object obj, String... getters) {
        Object v = callGetter(obj, getters);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static String getString(Object obj, String... getters) {
        Object v = callGetter(obj, getters);
        return v == null ? null : String.valueOf(v);
    }

    private static void setIfExists(Object obj, String method, Class<?> type, Object value) {
        try {
            Method m = obj.getClass().getMethod(method, type);
            m.invoke(obj, value);
        } catch (Exception ignored) {}
    }

    private Lot findLotByName(String name) {
        return lotRepo.findAll().stream()
                .filter(l -> eq(name, getString(l, "getName", "getCode", "toString")))
                .findFirst().orElse(null);
    }

    private Level findLevel(Lot lot, String levelName) {
        Long lotId = getLong(lot, "getId");
        return levelRepo.findAll().stream()
                .filter(v -> {
                    String n = getString(v, "getName", "getCode", "toString");
                    if (!eq(n, levelName)) return false;
                    Long vLotId = getLong(callGetter(v, "getLot"), "getId");
                    return Objects.equals(vLotId, lotId);
                })
                .findFirst().orElse(null);
    }
}
