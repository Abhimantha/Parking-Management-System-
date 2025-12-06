package com.example.parking.entity.convert;

import com.example.parking.entity.enums.SlotType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SafeSlotTypeConverter implements AttributeConverter<SlotType, String> {

    @Override
    public String convertToDatabaseColumn(SlotType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public SlotType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        String v = dbData.trim().toUpperCase(); // tolerate different cases
        try {
            return SlotType.valueOf(v);          // only valid enum names survive
        } catch (IllegalArgumentException ex) {
            // Unknown like "ALL" -> treat as null so Hibernate won't crash
            return null;
        }
    }
}
