package com.example.parking.service;

import com.example.parking.entity.PricingRule;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.DiscountType;
import com.example.parking.repository.PricingRuleRepository;
import com.example.parking.repository.PromotionRepository; // kept to not break wiring
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PricingEngineService {

    public static class Applied {
        public String kind; // "PROMO"
        public Long id;
        public String name;
        public DiscountType type;
        public BigDecimal value;
        public BigDecimal delta; // negative for discount
    }

    public static class Quote {
        public BigDecimal base = BigDecimal.ZERO;
        public BigDecimal total = BigDecimal.ZERO;
        public List<Applied> adjustments = new ArrayList<>();
    }

    private final PricingRuleRepository rules;
    @SuppressWarnings("unused")
    private final PromotionRepository promos; // not used now; kept to avoid bean breakage

    private static final Pattern CODE_TAG = Pattern.compile("\\[CODE:([A-Z0-9_\\-]+)]");

    public PricingEngineService(PricingRuleRepository rules, PromotionRepository promos) {
        this.rules = rules;
        this.promos = promos;
    }

    /* ---------- Public API ---------- */

    /** Total WITHOUT promo. */
    public BigDecimal calculateTotal(Reservation r) {
        try {
            Slot s = r.getSlot();
            if (s == null) return new BigDecimal("100.00");
            Quote q = quote(s, r.getStartTime(), r.getEndTime(), null);
            return q.total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal("100.00");
        }
    }

    /** Total WITH promo (if applicable). */
    public BigDecimal calculateTotal(Reservation r, String promoCode) {
        try {
            Slot s = r.getSlot();
            if (s == null) return new BigDecimal("100.00");
            Quote q = quote(s, r.getStartTime(), r.getEndTime(), promoCode);
            return q.total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal("100.00");
        }
    }

    /** Quote without promo preview. */
    public Quote quote(Slot slot, LocalDateTime start, LocalDateTime end) {
        return quote(slot, start, end, null);
    }

    /** Quote with promo preview. */
    public Quote quote(Slot slot, LocalDateTime start, LocalDateTime end, String promoCode) {
        Quote q = new Quote();

        if (slot == null || start == null || end == null || !end.isAfter(start)) {
            q.base = new BigDecimal("100.00");
            q.total = q.base;
            return q;
        }

        // --- Base price: rate * ceil(hours), min 1h; fallback 100.00 ---
        BigDecimal base;
        if (slot.getPriceRate() != null && slot.getPriceRate().compareTo(BigDecimal.ZERO) > 0) {
            long minutes = Duration.between(start, end).toMinutes();
            long hoursCeil = Math.max(1L, (minutes + 59) / 60);
            base = slot.getPriceRate().multiply(BigDecimal.valueOf(hoursCeil));
        } else {
            base = new BigDecimal("100.00");
        }
        q.base = base;
        BigDecimal running = base;

        // --- Apply promo only if a code was supplied ---
        String code = normalizeCode(promoCode);
        if (code != null) {
            List<PricingRule> active = rules.findByActiveTrueOrderByPriorityAscIdAsc();

            for (PricingRule pr : active) {
                String ruleCode = readPromoCode(pr);
                if (ruleCode == null || !ruleCode.equals(code)) continue;

                if (!isActiveFor(pr, start, end)) continue;

                DiscountType type = readType(pr);
                BigDecimal val = readValue(pr);

                BigDecimal disc = computeDiscount(running, type, val);
                if (disc.compareTo(BigDecimal.ZERO) > 0) {
                    Applied a = new Applied();
                    a.kind = "PROMO";
                    a.id = readId(pr);
                    a.name = readString(pr, "getName");
                    a.type = type;
                    a.value = val;
                    a.delta = disc.negate();
                    q.adjustments.add(a);

                    running = running.subtract(disc);
                    break; // first matching code wins
                }
            }
        }

        q.total = running.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return q;
    }

    /* ---------- helpers (reflection-safe) ---------- */

    private static Long readId(PricingRule pr){
        Object v = call(pr, "getId");
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? null : Long.valueOf(String.valueOf(v)); } catch (Exception e){ return null; }
    }

    private static String readPromoCode(PricingRule pr) {
        // 1) explicit promo getters if present
        String direct = readString(pr, "getPromoCode", "getCode", "getPromotionCode");
        if (direct != null && !direct.isBlank()) return normalizeCode(direct);

        // 2) parse [CODE:XYZ] from description if present
        String desc = readString(pr, "getDescription", "getDesc", "getNotes");
        String fromDesc = extractCode(desc);
        if (fromDesc != null) return fromDesc;

        // 3) parse [CODE:XYZ] from name if embedded there
        String name = readString(pr, "getName", "toString");
        return extractCode(name);
    }

    private static String extractCode(String text){
        if (text == null || text.isBlank()) return null;
        Matcher m = CODE_TAG.matcher(text.toUpperCase(Locale.ROOT));
        return m.find() ? m.group(1) : null;
    }

    private static boolean isActiveFor(PricingRule pr, LocalDateTime start, LocalDateTime end) {
        LocalDateTime from = readDateTime(pr, "getActiveFrom", "getStartAt", "getEffectiveFrom");
        LocalDateTime until = readDateTime(pr, "getActiveUntil", "getEndAt", "getEffectiveUntil");

        // If entity only has LocalDate startDate/endDate
        if (from == null) {
            LocalDate sd = readDate(pr, "getStartDate", "getEffectiveDateFrom");
            if (sd != null) from = sd.atStartOfDay();
        }
        if (until == null) {
            LocalDate ed = readDate(pr, "getEndDate", "getEffectiveDateUntil");
            if (ed != null) until = ed.atTime(23,59,59);
        }

        if (from != null && end.isBefore(from)) return false;
        if (until != null && start.isAfter(until)) return false;
        return true;
    }

    private static DiscountType readType(PricingRule pr){
        Object v = call(pr, "getType"); // enum?
        if (v instanceof DiscountType dt) return dt;

        String s = readString(pr, "getType", "getEffectType");
        if (s != null) {
            s = s.trim().toUpperCase(Locale.ROOT);
            if ("PERCENT".equals(s) || "PCT".equals(s)) return DiscountType.PERCENT;
            if ("FLAT".equals(s)) return DiscountType.FLAT;
        }
        return DiscountType.PERCENT;
    }

    private static BigDecimal readValue(PricingRule pr){
        Object v = call(pr, "getValue", "getEffectValue");
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v)); }
        catch (Exception e){ return BigDecimal.ZERO; }
    }

    private static String normalizeCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal computeDiscount(BigDecimal current, DiscountType type, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return switch (type) {
            case PERCENT -> current.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            case FLAT -> value.setScale(2, RoundingMode.HALF_UP);
        };
    }

    /* --- reflection utilities --- */
    private static Object call(Object obj, String... getters) {
        if (obj == null) return null;
        for (String g : getters) {
            try {
                Method m = obj.getClass().getMethod(g);
                return m.invoke(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }
    private static String readString(Object obj, String... getters){
        Object v = call(obj, getters);
        return v == null ? null : String.valueOf(v);
    }
    private static LocalDate readDate(Object obj, String... getters){
        Object v = call(obj, getters);
        if (v instanceof LocalDate ld) return ld;
        return null;
    }
    private static LocalDateTime readDateTime(Object obj, String... getters){
        Object v = call(obj, getters);
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof java.util.Date d) return Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return null;
    }
}
