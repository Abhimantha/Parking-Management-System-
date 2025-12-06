package com.example.parking.controller;

import com.example.parking.entity.Level;
import com.example.parking.entity.Lot;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.Slot;
import com.example.parking.entity.User;
import com.example.parking.repository.LevelRepository;
import com.example.parking.repository.LotRepository;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.repository.SlotRepository;
import com.example.parking.repository.UserRepository;
import com.example.parking.service.PricingEngineService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/find-parking")
public class FindParkingController {

    private final LotRepository lotRepo;
    private final LevelRepository levelRepo;
    private final SlotRepository slotRepo;
    private final ReservationRepository reservationRepo;
    private final UserRepository userRepo;

    private final PricingEngineService pricing;

    public FindParkingController(LotRepository lotRepo,
                                 LevelRepository levelRepo,
                                 SlotRepository slotRepo,
                                 ReservationRepository reservationRepo,
                                 UserRepository userRepo,
                                 PricingEngineService pricing) {
        this.lotRepo = lotRepo;
        this.levelRepo = levelRepo;
        this.slotRepo = slotRepo;
        this.reservationRepo = reservationRepo;
        this.userRepo = userRepo;
        this.pricing = pricing;
    }

    @GetMapping
    public String page(Model model,
                       @RequestParam(required = false) Long lotId,
                       @RequestParam(required = false) Long levelId,
                       @RequestParam(required = false) String start,
                       @RequestParam(required = false) String end,
                       @RequestParam(required = false) String msg,
                       @RequestParam(required = false) String err) {

        LocalDateTime startLdt = parseToLocalDateTime(start);
        LocalDateTime endLdt   = parseToLocalDateTime(end);

        List<Lot> lots = lotRepo.findAll();
        List<Level> levels = levelRepo.findAll();

        List<Slot> allSlots = slotRepo.findAll();
        List<Slot> levelSlots = new ArrayList<>(allSlots);

        if (levelId != null) {
            Level selectedLevel = levels.stream()
                    .filter(lv -> Objects.equals(getLong(lv, "getId"), levelId))
                    .findFirst().orElse(null);

            if (selectedLevel != null) {
                String selLevelName = getString(selectedLevel, "getName", "getCode", "getLabel", "getTitle");
                Long selLevelId = getLong(selectedLevel, "getId");

                levelSlots = allSlots.stream().filter(slot -> {
                    Object lvObj = callGetter(slot, "getLevel", "getLevelRef", "getLevelObj");
                    if (lvObj != null) {
                        Long slLevelId = getLong(lvObj, "getId");
                        if (slLevelId != null && selLevelId != null) {
                            return Objects.equals(slLevelId, selLevelId);
                        }
                        String slLevelName = getString(lvObj, "getName", "getCode", "getLabel", "toString");
                        if (slLevelName != null && selLevelName != null) {
                            return slLevelName.equalsIgnoreCase(selLevelName);
                        }
                    }
                    String slotLevelStr = getString(slot, "getLevel", "getLevelName", "getLevelCode");
                    if (slotLevelStr != null && selLevelName != null) {
                        return slotLevelStr.equalsIgnoreCase(selLevelName);
                    }
                    return true;
                }).collect(Collectors.toList());
            }
        }

        Set<Long> occupiedIds = new HashSet<>();
        if (startLdt != null && endLdt != null && endLdt.isAfter(startLdt)) {
            for (Reservation r : reservationRepo.findAll()) {
                if (r.getSlot() == null || r.getStartTime() == null || r.getEndTime() == null) continue;
                boolean overlaps = r.getStartTime().isBefore(endLdt) && r.getEndTime().isAfter(startLdt);
                if (overlaps) {
                    Long sid = getLong(r.getSlot(), "getId");
                    if (sid != null) occupiedIds.add(sid);
                }
            }
        }

        levelSlots.sort(Comparator.comparing(
                s -> Optional.ofNullable(getString(s, "getLabel", "getCode", "getName", "getNumber"))
                        .orElse(String.format("Slot #%s", String.valueOf(getLong(s, "getId"))))));

        DateTimeFormatter htmlDT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        model.addAttribute("lots", lots);
        model.addAttribute("levels", levels);
        model.addAttribute("selectedLotId", lotId);
        model.addAttribute("selectedLevelId", levelId);
        model.addAttribute("startStr", startLdt == null ? "" : startLdt.format(htmlDT));
        model.addAttribute("endStr",   endLdt   == null ? "" : endLdt.format(htmlDT));
        model.addAttribute("slots", levelSlots);
        model.addAttribute("occupiedIds", occupiedIds);
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);

        return "find_parking";
    }

    /** Reserve then redirect to /payments/{reservationId}/checkout?phase=ADVANCE[&promo=...] */
    @PostMapping("/reserve")
    public String reserve(@RequestParam Long slotId,
                          @RequestParam(required = false, name = "start") String start,
                          @RequestParam(required = false, name = "startAt") String startAt,
                          @RequestParam(required = false, name = "end") String end,
                          @RequestParam(required = false, name = "endAt") String endAt,
                          @RequestParam(required = false) Long lotId,
                          @RequestParam(required = false) Long levelId,
                          @RequestParam(required = false) String promo) {

        LocalDateTime s = parseToLocalDateTime(firstNonBlank(start, startAt));
        LocalDateTime e = parseToLocalDateTime(firstNonBlank(end,   endAt));

        if (s == null || e == null || !e.isAfter(s)) {
            return "redirect:/find-parking?err=Invalid%20time%20range&lotId=" + safe(lotId) + "&levelId=" + safe(levelId);
        }

        Slot slot = slotRepo.findById(slotId).orElse(null);
        if (slot == null) return "redirect:/find-parking?err=Slot%20not%20found";

        boolean conflict = reservationRepo.findAll().stream().anyMatch(r ->
                r.getSlot() != null &&
                        Objects.equals(getLong(r.getSlot(), "getId"), slotId) &&
                        r.getStartTime() != null && r.getEndTime() != null &&
                        r.getStartTime().isBefore(e) && r.getEndTime().isAfter(s)
        );
        if (conflict) {
            return "redirect:/find-parking?err=That%20slot%20is%20already%20taken&lotId=" + safe(lotId) + "&levelId=" + safe(levelId)
                    + "&start=" + enc(s.toString()) + "&end=" + enc(e.toString());
        }

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;
        var user = (email == null) ? null : userRepo.findByEmail(email).orElse(null);

        var res = new Reservation();
        res.setSlot(slot);
        res.setStartTime(s);
        res.setEndTime(e);
        if (user != null) res.setUser(user);
        res.setStatus(com.example.parking.entity.enums.ReservationStatus.ACTIVE);
        // store promo if user typed it on find page (optional)
        try { if (promo != null && !promo.isBlank()) res.setPromoCode(promo.trim()); } catch (Exception ignored) {}
        reservationRepo.save(res);

        // 🔁 Canonical path-style checkout; also carry promo + phase=ADVANCE
        String redirect = "/payments/" + res.getId() + "/checkout?phase=ADVANCE";
        if (promo != null && !promo.isBlank()) redirect += "&promo=" + enc(promo.trim());
        return "redirect:" + redirect;
    }

    /* ----------------- helpers ----------------- */
    private static String firstNonBlank(String a, String b){ return (a != null && !a.isBlank()) ? a : b; }
    private Object callGetter(Object obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            try { return obj.getClass().getMethod(n).invoke(obj); } catch (Exception ignored) {}
        }
        return null;
    }
    private Long getLong(Object obj, String... getterNames) {
        Object v = callGetter(obj, getterNames);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }
    private String getString(Object obj, String... getterNames) {
        Object v = callGetter(obj, getterNames);
        return (v == null) ? null : String.valueOf(v);
    }
    private String safe(Long id) { return (id == null) ? "" : id.toString(); }
    private static String enc(String s){ return URLEncoder.encode(String.valueOf(s), StandardCharsets.UTF_8); }

    private LocalDateTime parseToLocalDateTime(String v) {
        if (v == null || v.isBlank()) return null;
        try { return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignore) {}
        try { return LocalDateTime.parse(v); } catch (Exception ignore) {}
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); } catch (Exception ignore) {}
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); } catch (Exception ignore) {}
        try { return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(); } catch (Exception ignore) {}
        String cleaned = v.replaceAll("Z$", "").replaceAll("[+-]\\d\\d:?\\d\\d$", "");
        try { return LocalDateTime.parse(cleaned); } catch (Exception ignore) {}
        return null;
    }
}
