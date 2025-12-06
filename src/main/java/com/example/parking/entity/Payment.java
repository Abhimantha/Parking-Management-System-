// src/main/java/com/example/parking/entity/Payment.java
package com.example.parking.entity;

import com.example.parking.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) // allow multiple payments per reservation
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @NotNull
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    /** CARD / UPI / CASH … */
    @NotNull
    @Column(nullable = false)
    private String method;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status; // PENDING/PAID/REFUND_PENDING/REFUNDED/CANCELLED/FAILED

    /** DEPOSIT or BALANCE */
    @Column(nullable = false, length = 16)
    private String phase = "DEPOSIT";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;

    /** when REFUND_PENDING, auto move to REFUNDED at/after this time */
    private LocalDateTime refundDueAt;

    @Column(length = 255)
    private String cancelReason;

    @PrePersist
    public void prePersist() {
        if (status == null) status = PaymentStatus.PENDING;
        if (method == null || method.isBlank()) method = "CASH";
        if (phase == null || phase.isBlank()) phase = "DEPOSIT";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }


}
