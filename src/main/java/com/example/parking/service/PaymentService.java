package com.example.parking.service;

import com.example.parking.entity.Payment;
import com.example.parking.entity.Reservation;
import com.example.parking.entity.enums.PaymentStatus;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.PaymentRepository;
import com.example.parking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PaymentService {

    private static final String PHASE_ADVANCE = "ADVANCE";
    private static final String PHASE_BALANCE = "BALANCE";

    private final PaymentRepository payments;
    private final ReservationRepository reservations;
    private final SystemSettingsService settings;
    private final PricingEngineService pricing;

    public PaymentService(PaymentRepository payments,
                          ReservationRepository reservations,
                          SystemSettingsService settings,
                          PricingEngineService pricing) {
        this.payments = payments;
        this.reservations = reservations;
        this.settings = settings;
        this.pricing = pricing;
    }

    // ------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------
    public List<Payment> findAll() {
        List<Payment> list = payments.findAllByOrderByCreatedAtDesc();
        return (list != null && !list.isEmpty()) ? list : payments.findAll();
    }

    public List<Payment> findAllByUserId(Long userId) {
        if (userId == null) return List.of();
        return payments.findByReservation_User_Id(userId);
    }

    // ------------------------------------------------------------
    // Core operations
    // ------------------------------------------------------------
    public Payment create(Payment p, Long reservationId) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        String phase = (p.getPhase() == null || p.getPhase().isBlank()) ? PHASE_ADVANCE : p.getPhase();
        Payment existing = findExisting(reservationId, phase);

        if (existing != null) {
            if (p.getAmount() != null) existing.setAmount(p.getAmount());
            if (p.getMethod() != null && !p.getMethod().isBlank()) existing.setMethod(p.getMethod());
            if (p.getStatus() != null) existing.setStatus(p.getStatus());
            return payments.save(existing);
        }

        p.setReservation(r);
        p.setPhase(phase);
        if (p.getStatus() == null) p.setStatus(PaymentStatus.PENDING);
        if (p.getMethod() == null || p.getMethod().isBlank()) p.setMethod("CASH");
        if (p.getCreatedAt() == null) p.setCreatedAt(LocalDateTime.now());
        return payments.save(p);
    }

    public void markPaid(Long paymentId, String method) {
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        if (method != null && !method.isBlank()) p.setMethod(method);
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now());
        payments.save(p);
    }

    public void cancel(Long paymentId, String reason) {
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        p.setStatus(PaymentStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) p.setCancelReason(reason);
        p.setCancelledAt(LocalDateTime.now());
        payments.save(p);
    }

    public BigDecimal totalPaid(Long reservationId) {
        return paymentsForReservation(reservationId).stream()
                .filter(pp -> pp.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Payment ensureAdvance(Long reservationId, BigDecimal amount) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        Payment adv = findExisting(reservationId, PHASE_ADVANCE);
        if (adv != null) {
            if (adv.getStatus() != PaymentStatus.PAID) {
                adv.setAmount(amount);
                adv.setStatus(PaymentStatus.PENDING);
                adv.setPaidAt(null);
            }
            return payments.save(adv);
        }

        Payment p = new Payment();
        p.setReservation(r);
        p.setPhase(PHASE_ADVANCE);
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING);
        p.setMethod("CASH");
        p.setCreatedAt(LocalDateTime.now());
        return payments.save(p);
    }

    public Payment ensureBalance(Long reservationId, BigDecimal amount) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        Payment bal = findExisting(reservationId, PHASE_BALANCE);
        if (bal != null) {
            if (bal.getStatus() != PaymentStatus.PAID) {
                bal.setAmount(amount.max(BigDecimal.ZERO));
                bal.setStatus(PaymentStatus.PENDING);
                bal.setPaidAt(null);
            }
            return payments.save(bal);
        }

        Payment p = new Payment();
        p.setReservation(r);
        p.setPhase(PHASE_BALANCE);
        p.setAmount(amount.max(BigDecimal.ZERO));
        p.setStatus(PaymentStatus.PENDING);
        p.setMethod("CASH");
        p.setCreatedAt(LocalDateTime.now());
        return payments.save(p);
    }

    /** Dynamic pricing with safe fallback. */
    public BigDecimal calculateTotal(Reservation r) {
        try {
            return pricing.calculateTotal(r);
        } catch (Exception ignored) {
            return new BigDecimal("100.00");
        }
    }

    /** NEW: dynamic pricing with explicit promo. */
    public BigDecimal calculateTotal(Reservation r, String promo) {
        try {
            return pricing.calculateTotal(r, promo);
        } catch (Exception ignored) {
            return new BigDecimal("100.00");
        }
    }

    public void queueRefundsOnReservationCancel(Long reservationId, int hours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now.plusHours(hours <= 0 ? 24 : hours);

        for (Payment p : paymentsForReservation(reservationId)) {
            switch (p.getStatus()) {
                case PAID -> {
                    p.setStatus(PaymentStatus.REFUNDED);
                    p.setRefundDueAt(due);
                    p.setCancelledAt(now);
                    if (p.getCancelReason() == null || p.getCancelReason().isBlank()) {
                        p.setCancelReason("Reservation cancelled");
                    }
                }
                case PENDING -> {
                    p.setStatus(PaymentStatus.CANCELLED);
                    p.setCancelledAt(now);
                    if (p.getCancelReason() == null || p.getCancelReason().isBlank()) {
                        p.setCancelReason("Reservation cancelled");
                    }
                }
                default -> {}
            }
            payments.save(p);
        }
    }

    public void markAllPaymentsRefunded(Long reservationId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> all = payments.findAllByOrderByCreatedAtDesc();
        if (all == null || all.isEmpty()) all = payments.findAll();

        for (Payment p : all) {
            Reservation res = p.getReservation();
            if (res != null && res.getId() != null && res.getId().equals(reservationId)) {
                if (p.getStatus() != PaymentStatus.REFUNDED) {
                    p.setStatus(PaymentStatus.REFUNDED);
                    if (reason != null && !reason.isBlank()) p.setCancelReason(reason);
                    p.setCancelledAt(now);
                    payments.save(p);
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------
    private Payment findExisting(Long reservationId, String phase) {
        List<Payment> all = payments.findAllByOrderByCreatedAtDesc();
        if (all == null || all.isEmpty()) all = payments.findAll(); // <-- fix
        for (Payment p : all) {
            Reservation res = p.getReservation();
            if (res != null && res.getId() != null
                    && res.getId().equals(reservationId)
                    && phase.equalsIgnoreCase(String.valueOf(p.getPhase()))) {
                return p;
            }
        }
        return null;
    }


    private List<Payment> paymentsForReservation(Long reservationId) {
        List<Payment> all = payments.findAllByOrderByCreatedAtDesc();
        if (all == null || all.isEmpty()) all = payments.findAll();
        final Long rid = reservationId;
        return all.stream()
                .filter(p -> p.getReservation() != null
                        && p.getReservation().getId() != null
                        && p.getReservation().getId().equals(rid))
                .toList();
    }
}
