package com.example.parking.repository;

import com.example.parking.entity.Reservation;
import com.example.parking.entity.enums.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatus(ReservationStatus status);
    List<Reservation> findByUser_Id(Long userId);
    List<Reservation> findByUserEmailOrderByStartTimeDesc(String email);

    @EntityGraph(attributePaths = { "user" })
    List<Reservation> findAllByOrderByStartTimeDesc(Pageable pageable);

    boolean existsBySlot_IdAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Long slotId, Collection<ReservationStatus> statuses,
            LocalDateTime end, LocalDateTime start);

    List<Reservation> findByUserIdOrderByStartTimeDesc(Long userId);

    boolean existsBySlotIdAndEndTimeAfterAndStartTimeBefore(
            Long slotId, Instant startAt, Instant endAt
    );

    // --- NEW: simplest, time-agnostic: “reserved if any ACTIVE reservation exists”
    boolean existsBySlot_IdAndStatus(Long slotId, ReservationStatus status);

    // --- Optional (kept if you later want time-sensitive logic):
    @Query("""
        select (count(r) > 0) from Reservation r
         where r.slot.id = :slotId
           and r.status in :statuses
           and (r.endTime is null or r.endTime >= :now)
    """)
    boolean hasActiveOrOpenEnded(
            @Param("slotId") Long slotId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
