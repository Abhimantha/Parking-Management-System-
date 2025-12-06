package com.example.parking.service;

import com.example.parking.entity.SystemSettings;
import com.example.parking.web.FeatureDisabledException;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
public class FeatureGate {
    private final SystemSettingsService settings;

    public FeatureGate(SystemSettingsService settings) {
        this.settings = settings;
    }

    /** Throws if reservations are disabled */
    public void ensureReservationsEnabled() {
        SystemSettings s = settings.get();
        if (!s.isEnableReservations()) {
            throw new FeatureDisabledException("Reservations are currently disabled by an administrator.");
        }
    }

    /** Throws if payments are disabled */
    public void ensurePaymentsEnabled() {
        SystemSettings s = settings.get();
        if (!s.isEnablePayments()) {
            throw new FeatureDisabledException("Payments are currently disabled by an administrator.");
        }
    }

    /**
     * Throws if cancellation is not allowed OR a reason is required and missing.
     * @param reason the user-provided reason (may be null/blank)
     */
    public void ensureCancellationAllowed(String reason) {
        SystemSettings s = settings.get();
        if (!s.isAllowCancellation()) {
            throw new FeatureDisabledException("Cancellation is not allowed for this system right now.");
        }
        if (s.isRequireCancellationReason()) {
            if (reason == null || reason.trim().isEmpty()) {
                throw new FeatureDisabledException("A cancellation reason is required.");
            }
        }
    }

    /**
     * Refund policy: allow refund only if the cancellation happens at least N hours BEFORE start.
     */
    public boolean eligibleForRefund(LocalDateTime reservationStart, Clock clock) {
        SystemSettings s = settings.get();
        int hours = Math.max(0, s.getRefundWindowHours());
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isAfter(reservationStart.minusHours(hours));
    }

    public boolean eligibleForRefund(Instant reservationStartUtc, Clock clock) {
        return eligibleForRefund(LocalDateTime.ofInstant(reservationStartUtc, ZoneOffset.UTC), clock);
    }

    /** True if drivers are allowed to view reports */
    public boolean driversCanSeeReports() {
        return settings.get().isShowReportsToDrivers();
    }
}
