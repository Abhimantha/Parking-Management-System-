package com.example.parking.repository;

import com.example.parking.entity.Payment;
import com.example.parking.entity.enums.PaymentPhase;
import com.example.parking.entity.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // already in your file; keep them
    List<Payment> findByReservation_User_Id(Long userId);
    List<Payment> findByReservationUserId(Long userId);
    List<Payment> findByReservationUserEmailOrderByCreatedAtDesc(String email);
    List<Payment> findByReservation_User_EmailOrderByCreatedAtDesc(String email);
    List<Payment> findAllByOrderByCreatedAtDesc();

    // ...
    Optional<Payment> findFirstByReservationIdOrderByIdDesc(Long reservationId);
    List<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Payment> findAllByReservation_Id(Long reservationId);

    Optional<Payment> findTopByReservation_IdAndPhaseIgnoreCaseOrderByCreatedAtDesc(Long reservationId, String phase);

    long countByStatus(PaymentStatus status);

    @Query("""
     select coalesce(sum(p.amount), 0)
     from Payment p
     where p.reservation.id = :rid and p.status = com.example.parking.entity.enums.PaymentStatus.PAID
  """)
    BigDecimal sumPaidByReservation(@Param("rid") Long reservationId);

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    // NEW: idempotent lookup for the phase of a reservation
    Optional<Payment> findByReservation_IdAndPhase(Long reservationId, PaymentPhase phase);

    Optional<Payment> findFirstByReservation_IdAndPhase(Long reservationId, String phase);

    // NEW: all payments for a reservation
    List<Payment> findByReservation_Id(Long reservationId);

    // NEW: total of PAID amounts for the reservation
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.reservation.id = :reservationId and p.status = 'PAID'")
    BigDecimal totalPaidForReservation(Long reservationId);
}
