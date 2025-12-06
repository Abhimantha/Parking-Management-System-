package com.example.parking.entity;

import com.example.parking.entity.enums.DiscountType;
import com.example.parking.entity.enums.PriceScope;
import com.example.parking.entity.enums.TimeBandType;
import com.example.parking.entity.enums.SlotType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Convert;
import com.example.parking.entity.convert.SafeSlotTypeConverter;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=120)
    private String name;

    // NEW: allow filtering PRICING vs PROMO rows that live in the same table
    @Column(name = "rule_type", length = 16)
    private String ruleType; // "PRICING" or "PROMO"

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private PriceScope scope = PriceScope.GLOBAL;

    // target filters (nullable depending on scope)
    private String lot;
    private String level;
    @Convert(converter = SafeSlotTypeConverter.class)
    private SlotType slotType;
    private Long slotId;

    // time band
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private TimeBandType timeBand = TimeBandType.DAILY_WINDOW;

    private LocalDateTime activeFrom;
    private LocalDateTime activeUntil;
    private Integer startHour; // 0-23
    private Integer endHour;   // 0-23

    // adjustment (+)
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=10)
    private DiscountType type = DiscountType.PERCENT;

    @Column(nullable=false, precision=18, scale=2)
    private BigDecimal value = BigDecimal.ZERO; // percent or flat add

    @Column(nullable=false)
    private boolean active = true;

    @Column(nullable=false)
    private int priority = 0; // higher last or first; engine will sort

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PriceScope getScope() { return scope; }
    public void setScope(PriceScope scope) { this.scope = scope; }
    public String getLot() { return lot; }
    public void setLot(String lot) { this.lot = lot; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public SlotType getSlotType() { return slotType; }
    public void setSlotType(SlotType slotType) { this.slotType = slotType; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public TimeBandType getTimeBand() { return timeBand; }
    public void setTimeBand(TimeBandType timeBand) { this.timeBand = timeBand; }
    public LocalDateTime getActiveFrom() { return activeFrom; }
    public void setActiveFrom(LocalDateTime activeFrom) { this.activeFrom = activeFrom; }
    public LocalDateTime getActiveUntil() { return activeUntil; }
    public void setActiveUntil(LocalDateTime activeUntil) { this.activeUntil = activeUntil; }
    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }
    public Integer getEndHour() { return endHour; }
    public void setEndHour(Integer endHour) { this.endHour = endHour; }
    public DiscountType getType() { return type; }
    public void setType(DiscountType type) { this.type = type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
}
