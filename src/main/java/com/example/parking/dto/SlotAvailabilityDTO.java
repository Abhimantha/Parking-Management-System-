package com.example.parking.dto;

public class SlotAvailabilityDTO {
    public Long id;
    public String slotNumber;
    public String level;
    public String type;
    public String status;    // current status field
    public boolean available; // status==AVAILABLE AND no overlaps in window
    public int rowIndex;
    public int colIndex;


    public SlotAvailabilityDTO() {}
    public SlotAvailabilityDTO(Long id, String slotNumber, String level, String type, String status,
                               boolean available, int rowIndex, int colIndex) {
        this.id = id; this.slotNumber = slotNumber; this.level = level; this.type = type; this.status = status;
        this.available = available; this.rowIndex = rowIndex; this.colIndex = colIndex;
    }
}
