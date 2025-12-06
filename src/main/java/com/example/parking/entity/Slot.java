package com.example.parking.entity;

import com.example.parking.entity.enums.SlotStatus;
import com.example.parking.entity.enums.SlotType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Entity
@Table(name = "slots")
public class Slot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Column(nullable = false, unique = true)
    private String slotNumber; // e.g., A1, B12

    @NotBlank @Column(nullable = false)
    private String level;      // e.g., L1, Basement

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotType type = SlotType.COMPACT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.AVAILABLE;


    // add these fields inside the entity
    @Column
    private String lot;            // e.g., "City Center", "West Garage"

    @Column(name="map_row")
    private Integer mapRow;        // grid row (1-based)

    @Column(name="map_col")
    private Integer mapCol;        // grid col (1-based)

    @Column(precision = 10, scale = 2)
    private BigDecimal priceRate = BigDecimal.ZERO;


    // getters & setters
    public BigDecimal getPriceRate() { return priceRate; }
    public void setPriceRate(BigDecimal priceRate) { this.priceRate = priceRate; }
    public String getLot() { return lot; }
    public void setLot(String lot) { this.lot = lot; }
    public Integer getMapRow() { return mapRow; }
    public void setMapRow(Integer mapRow) { this.mapRow = mapRow; }
    public Integer getMapCol() { return mapCol; }
    public void setMapCol(Integer mapCol) { this.mapCol = mapCol; }



    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public SlotType getType() { return type; }
    public void setType(SlotType type) { this.type = type; }

    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
