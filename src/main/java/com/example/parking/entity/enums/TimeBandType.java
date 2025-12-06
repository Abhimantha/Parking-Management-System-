package com.example.parking.entity.enums;

public enum TimeBandType {
    EXACT_RANGE,   // uses startsAt, endsAt (LocalDateTime)
    WEEKEND,       // Sat/Sun
    WEEKDAY,       // Mon..Fri
    DAILY_WINDOW   // uses windowStart, windowEnd (LocalTime) applied every day
}
