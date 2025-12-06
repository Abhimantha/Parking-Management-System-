package com.example.parking.entity.enums;

public enum PriceScope {
    GLOBAL,      // applies everywhere
    LOT,         // matches Slot.lot
    LEVEL,       // matches Slot.level
    SLOT_TYPE,   // matches Slot.type
    SLOT         // matches specific Slot.id
}
