// src/main/java/com/example/parking/service/SystemSettingsService.java
package com.example.parking.service;

import com.example.parking.entity.SystemSettings;
import com.example.parking.repository.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {
    private final SystemSettingsRepository repo;

    public SystemSettingsService(SystemSettingsRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public SystemSettings get() {
        return repo.findById(1L).orElseGet(() -> {
            var s = new SystemSettings();
            s.setId(1L);
            s.setEnableReservations(true);
            s.setEnablePayments(true);
            s.setAllowCancellation(true);
            s.setRefundWindowHours(24);
            s.setRequireCancellationReason(true);
            s.setShowReportsToDrivers(true);
            return repo.save(s);
        });
    }

    @Transactional
    public void update(SystemSettings incoming) {
        var s = get();
        s.setEnableReservations(incoming.isEnableReservations());
        s.setEnablePayments(incoming.isEnablePayments());
        s.setAllowCancellation(incoming.isAllowCancellation());
        s.setRefundWindowHours(incoming.getRefundWindowHours());
        s.setRequireCancellationReason(incoming.isRequireCancellationReason());
        s.setShowReportsToDrivers(incoming.isShowReportsToDrivers());
        repo.save(s);
    }
}
