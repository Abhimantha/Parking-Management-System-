package com.example.parking.entity;

import com.example.parking.entity.enums.DiscountType;
import com.example.parking.entity.enums.PriceScope;
import com.example.parking.entity.enums.TimeBandType;
import com.example.parking.entity.enums.SlotType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=120)
    private String name;

    @Column(length=40)
    private String code; // optional, keep null for auto-apply promos

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private PriceScope scope = PriceScope.GLOBAL;

    private String lot;
    private String level;
    @Enumerated(EnumType.STRING)
    private SlotType slotType;
    private Long slotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private TimeBandType timeBand = TimeBandType.DAILY_WINDOW;

    private LocalDateTime activeFrom;
    private LocalDateTime activeUntil;
    private Integer startHour;
    private Integer endHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=10)
    private DiscountType type = DiscountType.PERCENT;

    @Column(nullable=false, precision=18, scale=2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(nullable=false)
    private boolean active = true;

    @Column(nullable=false)
    private boolean stackable = true;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
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
    public boolean isStackable() { return stackable; }
    public void setStackable(boolean stackable) { this.stackable = stackable; }
}
