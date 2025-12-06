package com.example.parking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Promos admin (schema-flexible):
 * - Supports columns: effect_type/effect_value and/or `type`/`value`
 * - Uses created_at/updated_at if present
 * - Stores promo code in name as [CODE:XYZ] (engine reads from name)
 */
@Controller
public class PricingAdminController {

    private final JdbcTemplate jdbc;

    // Lazily detected table columns (lowercase)
    private volatile Set<String> cols;

    public PricingAdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/pricing")
    public String page(Authentication auth, Model model) {
        enforceAdminOrManager(auth);
        model.addAttribute("title", "Pricing & Promos");
        return "pricing";
    }

    /* ===================== API ===================== */

    @GetMapping("/api/pricing/rules")
    @ResponseBody
    public List<PriceRule> listRules(Authentication auth,
                                     @RequestParam(required = false) Boolean activeOnly,
                                     @RequestParam(required = false) Boolean current,
                                     @RequestParam(required = false) String type // e.g. PROMO

    ) {
        enforceAdminOrManager(auth);
        ensureCols();

        String select = buildSelectSql() + " order by active desc, priority asc, id desc";
        return jdbc.query(select, mapper());
    }

    @PostMapping("/api/pricing")
    @ResponseBody
    public PriceRule create(Authentication auth, @RequestBody PriceRule body) {
        enforceAdminOrManager(auth);
        validate(body, true);
        ensureCols();

        final String ruleType = def(body.ruleType, "PROMO");
        final String namePacked = packName(body.name, body.promoCode);
        final Timestamp tsFrom = tsStart(body.startDate);
        final Timestamp tsUntil = tsEnd(body.endDate);
        final String effType = def(body.effectType, "PERCENT");
        final BigDecimal effValue = nz(body.effectValue);
        final boolean active = bool(body.active, true);
        final int priority = 100;

        String insert = buildInsertSql();
        Object[] args = buildInsertArgs(ruleType, namePacked, tsFrom, tsUntil, effType, effValue, active, priority);

        KeyHolder kh = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < args.length; i++) {
                    Object v = args[i];
                    if (v instanceof Timestamp t) ps.setTimestamp(i + 1, t);
                    else if (v instanceof BigDecimal bd) ps.setBigDecimal(i + 1, bd);
                    else if (v instanceof Boolean b) ps.setBoolean(i + 1, b);
                    else if (v instanceof Integer n) ps.setInt(i + 1, n);
                    else ps.setObject(i + 1, v);
                }
                return ps;
            }, kh);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, root(ex));
        }

        Long id = kh.getKey() == null ? null : kh.getKey().longValue();
        if (id == null) {
            // Portable fallback
            id = jdbc.queryForObject(
                    "select id from pricing_rules where name=? order by id desc limit 1",
                    Long.class, namePacked
            );
        }

        return jdbc.queryForObject(buildSelectSql() + " where id=?", mapper(), id);
    }

    @PutMapping("/api/pricing/{id}")
    @ResponseBody
    public PriceRule update(Authentication auth, @PathVariable Long id, @RequestBody PriceRule body) {
        enforceAdminOrManager(auth);
        validate(body, false);
        ensureCols();

        final String ruleType = def(body.ruleType, "PROMO");
        final String namePacked = packName(body.name, body.promoCode);
        final Timestamp tsFrom = tsStart(body.startDate);
        final Timestamp tsUntil = tsEnd(body.endDate);
        final String effType = def(body.effectType, "PERCENT");
        final BigDecimal effValue = nz(body.effectValue);
        final boolean active = bool(body.active, true);
        final int priority = 100;

        String update = buildUpdateSql();
        Object[] args = buildUpdateArgs(ruleType, namePacked, tsFrom, tsUntil, effType, effValue, active, priority, id);

        int n;
        try {
            n = jdbc.update(update, args);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, root(ex));
        }
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");

        return jdbc.queryForObject(buildSelectSql() + " where id=?", mapper(), id);
    }

    @PatchMapping("/api/pricing/{id}/active")
    @ResponseBody
    public PriceRule toggleActive(Authentication auth, @PathVariable Long id,
                                  @RequestParam boolean active) {
        enforceAdminOrManager(auth);
        ensureCols();

        String sql = has("updated_at")
                ? "update pricing_rules set active=?, updated_at=now() where id=?"
                : "update pricing_rules set active=? where id=?";
        int n;
        try {
            n = jdbc.update(sql, active, id);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, root(ex));
        }
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        return jdbc.queryForObject(buildSelectSql() + " where id=?", mapper(), id);
    }

    @DeleteMapping("/api/pricing/{id}")
    @ResponseBody
    public void delete(Authentication auth, @PathVariable Long id) {
        enforceAdminOrManager(auth);
        ensureCols();
        int n;
        try {
            n = jdbc.update("delete from pricing_rules where id=?", id);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, root(ex));
        }
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
    }

    @GetMapping("/api/pricing/deals")
    @ResponseBody
    public List<PriceRule> publicDeals(
            @RequestParam(value = "current", defaultValue = "true") boolean current,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {

        ensureCols();

        StringBuilder sb = new StringBuilder(buildSelectSql())
                .append(" where 1=1");

        // Only PROMO rows when rule_type column exists
        if (has("rule_type")) {
            sb.append(" and rule_type = 'PROMO'");
        }

        // Only active rows when active column exists
        if (activeOnly && has("active")) {
            sb.append(" and active = true");
        }

        // Only rows that are currently in window (null bounds are ok)
        if (current) {
            sb.append(" and ( (active_from is null or active_from <= now())")
                    .append(" and  (active_until is null or active_until >= now()) )");
        }

        sb.append(" order by priority asc, id desc");

        List<PriceRule> list = jdbc.query(sb.toString(), mapper());

        // If there is no rule_type column, filter to things that actually look like promos
        // (we packed promoCode into the name and the mapper extracts it)
        if (!has("rule_type")) {
            list.removeIf(r -> r == null || r.promoCode == null || r.promoCode.isBlank());
        }
        return list;
    }


    /* ================== helpers =================== */

    private void ensureCols() {
        if (cols != null) return;
        synchronized (this) {
            if (cols != null) return;
            Set<String> set = new HashSet<>();
            try (Connection c = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
                DatabaseMetaData md = c.getMetaData();
                try (ResultSet rs = md.getColumns(c.getCatalog(), null, "%", "%")) {
                    while (rs.next()) {
                        String table = rs.getString("TABLE_NAME");
                        if (table != null && table.equalsIgnoreCase("pricing_rules")) {
                            String col = rs.getString("COLUMN_NAME");
                            if (col != null) set.add(col.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (Exception ignored) {}
            cols = set;
        }
    }

    private boolean has(String col) { return cols.contains(col.toLowerCase(Locale.ROOT)); }

    private static void enforceAdminOrManager(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        boolean ok = auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ROLE_MANAGER".equals(r);
        });
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static void validate(PriceRule r, boolean creating) {
        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body required");
        if (r.name == null || r.name.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }

    private static String root(Throwable ex){
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return (t.getMessage() == null || t.getMessage().isBlank()) ? ex.toString() : t.getMessage();
    }

    /* ===== SQL builders (schema-flex) ===== */

    private String buildSelectSql() {
        // choose one pair and alias to etype/evalue
        String typePair = has("type") && has("value")
                ? "`type` as etype, `value` as evalue"
                : "effect_type as etype, effect_value as evalue";
        String created = has("created_at") ? ", created_at" : "";
        String updated = has("updated_at") ? ", updated_at" : "";
        return "select id, rule_type, name, active_from, active_until, " + typePair +
                ", active, priority" + created + updated + " from pricing_rules";
    }

    private String buildInsertSql() {
        List<String> cols = new ArrayList<>(List.of(
                "rule_type", "name", "active_from", "active_until"
        ));
        if (has("effect_type")) cols.add("effect_type");
        if (has("effect_value")) cols.add("effect_value");
        if (has("type")) cols.add("`type`");
        if (has("value")) cols.add("`value`");
        cols.add("active");
        cols.add("priority");
        if (has("created_at")) cols.add("created_at");
        if (has("updated_at")) cols.add("updated_at");

        StringBuilder sb = new StringBuilder("insert into pricing_rules (");
        sb.append(String.join(", ", cols)).append(") values (");

        List<String> qs = new ArrayList<>();
        for (String c : cols) {
            if ("created_at".equalsIgnoreCase(c) || "updated_at".equalsIgnoreCase(c)) {
                qs.add("now()");
            } else {
                qs.add("?");
            }
        }
        sb.append(String.join(", ", qs)).append(")");
        return sb.toString();
    }

    private Object[] buildInsertArgs(String ruleType, String name, Timestamp from, Timestamp until,
                                     String effType, BigDecimal effVal, boolean active, int priority) {
        List<Object> args = new ArrayList<>(8);
        args.add(ruleType);
        args.add(name);
        args.add(from);
        args.add(until);
        if (has("effect_type")) args.add(effType);
        if (has("effect_value")) args.add(effVal);
        if (has("type")) args.add(effType);
        if (has("value")) args.add(effVal);
        args.add(active);
        args.add(priority);
        // created_at / updated_at are now() literals, not args
        return args.toArray();
    }

    private String buildUpdateSql() {
        List<String> sets = new ArrayList<>(List.of(
                "rule_type=?", "name=?", "active_from=?", "active_until=?"
        ));
        if (has("effect_type")) sets.add("effect_type=?");
        if (has("effect_value")) sets.add("effect_value=?");
        if (has("type")) sets.add("`type`=?");
        if (has("value")) sets.add("`value`=?");
        sets.add("active=?");
        sets.add("priority=?");
        if (has("updated_at")) sets.add("updated_at=now()");

        return "update pricing_rules set " + String.join(", ", sets) + " where id=?";
    }

    private Object[] buildUpdateArgs(String ruleType, String name, Timestamp from, Timestamp until,
                                     String effType, BigDecimal effVal, boolean active, int priority, Long id) {
        List<Object> args = new ArrayList<>();
        args.add(ruleType);
        args.add(name);
        args.add(from);
        args.add(until);
        if (has("effect_type")) args.add(effType);
        if (has("effect_value")) args.add(effVal);
        if (has("type")) args.add(effType);
        if (has("value")) args.add(effVal);
        args.add(active);
        args.add(priority);
        args.add(id);
        return args.toArray();
    }

    /* ===== promo tag inside name ===== */
    private static final Pattern CODE_TAG = Pattern.compile("\\[CODE:([A-Z0-9_\\-]+)]");

    private static String packName(String rawName, String promoCode){
        String base = (rawName == null ? "" : rawName).replaceAll("\\s*\\[CODE:.*?]\\s*", " ").trim();
        if (promoCode == null || promoCode.isBlank()) return base;
        String token = "[CODE:" + promoCode.trim().toUpperCase(Locale.ROOT) + "]";
        return base.isEmpty() ? token : (base + " " + token);
    }

    private static java.sql.Timestamp tsStart(LocalDate d) { return d == null ? null : java.sql.Timestamp.valueOf(d.atStartOfDay()); }
    private static java.sql.Timestamp tsEnd(LocalDate d)   { return d == null ? null : java.sql.Timestamp.valueOf(d.atTime(23,59,59)); }

    private static String  def(String v, String d) { return (v == null || v.isBlank()) ? d : v; }
    private static BigDecimal nz(BigDecimal v)     { return v == null ? new BigDecimal("0.00") : v; }
    private static boolean bool(Boolean v, boolean d){ return v == null ? d : v; }

    private static RowMapper<PriceRule> mapper() {
        return new RowMapper<>() {
            @Override public PriceRule mapRow(ResultSet rs, int rowNum) throws SQLException {
                PriceRule r = new PriceRule();
                r.id = rs.getLong("id");
                r.ruleType = rs.getString("rule_type");
                r.name = rs.getString("name");
                Timestamp from = getTs(rs, "active_from");
                Timestamp until = getTs(rs, "active_until");
                r.startDate = (from == null) ? null : from.toLocalDateTime().toLocalDate();
                r.endDate   = (until == null) ? null : until.toLocalDateTime().toLocalDate();
                r.effectType  = rs.getString("etype");     // aliased
                r.effectValue = rs.getBigDecimal("evalue");// aliased
                r.active = rs.getBoolean("active");
                r.priority = rs.getInt("priority");
                r.createdAt = toLdt(getTs(rs, "created_at"));
                r.updatedAt = toLdt(getTs(rs, "updated_at"));
                // promo code appears inside name
                r.promoCode = extractCodeFromName(r.name);
                return r;
            }
            private Timestamp getTs(ResultSet rs, String col) {
                try { return rs.getTimestamp(col); } catch (SQLException e) { return null; }
            }
            private LocalDateTime toLdt(Timestamp ts){ return ts == null ? null : ts.toLocalDateTime(); }
        };
    }

    private static String extractCodeFromName(String name){
        if (name == null || name.isBlank()) return null;
        Matcher m = CODE_TAG.matcher(name.toUpperCase(Locale.ROOT));
        return m.find() ? m.group(1) : null;
    }

    /** DTO used by UI */
    public static class PriceRule {
        public Long id;
        public String ruleType;
        public String name;
        public String promoCode;
        public LocalDate startDate;
        public LocalDate endDate;
        public String effectType;       // "PERCENT" or "FLAT"
        public BigDecimal effectValue;  // percent or flat
        public Integer priority;
        public Boolean active;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }
}
