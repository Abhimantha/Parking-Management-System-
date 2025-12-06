package com.example.parking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "system_settings")
public class SystemSettings {
    @Id
    private Long id = 1L;

    @Column(nullable = false) private boolean enableReservations = true;
    @Column(nullable = false) private boolean enablePayments = true;
    @Column(nullable = false) private boolean allowCancellation = true;
    @Column(nullable = false) private boolean requireCancellationReason = true;
    @Column(nullable = false) private int refundWindowHours = 24;
    @Column(nullable = false) private boolean showReportsToDrivers = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isEnableReservations() { return enableReservations; }
    public void setEnableReservations(boolean enableReservations) { this.enableReservations = enableReservations; }

    public boolean isEnablePayments() { return enablePayments; }
    public void setEnablePayments(boolean enablePayments) { this.enablePayments = enablePayments; }

    public boolean isAllowCancellation() { return allowCancellation; }
    public void setAllowCancellation(boolean allowCancellation) { this.allowCancellation = allowCancellation; }

    public boolean isRequireCancellationReason() { return requireCancellationReason; }
    public void setRequireCancellationReason(boolean requireCancellationReason) { this.requireCancellationReason = requireCancellationReason; }

    public int getRefundWindowHours() { return refundWindowHours; }
    public void setRefundWindowHours(int refundWindowHours) { this.refundWindowHours = refundWindowHours; }

    public boolean isShowReportsToDrivers() { return showReportsToDrivers; }
    public void setShowReportsToDrivers(boolean showReportsToDrivers) { this.showReportsToDrivers = showReportsToDrivers; }
}
