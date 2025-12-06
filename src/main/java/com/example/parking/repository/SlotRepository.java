package com.example.parking.repository;

import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByStatus(SlotStatus status);
    boolean existsBySlotNumber(String slotNumber);



    List<Slot> findByLotAndLevelOrderByMapRowAscMapColAsc(String lot, String level);
    List<Slot> findByLevelOrderByMapRowAscMapColAsc(String level);
    List<Slot> findByLotOrderByMapRowAscMapColAsc(String lot);

    @Query("""
        select s
        from Slot s
        where not exists (
          select 1 from Reservation r
          where r.slot.id = s.id
            and r.endTime   > :startAt
            and r.startTime < :endAt
        )
    """)
    List<Slot> findAvailable(@Param("lotId")   Long lotId,    // kept for signature compatibility (ignored)
                             @Param("levelId") Long levelId,  // kept for signature compatibility (ignored)
                             @Param("startAt") Instant startAt,
                             @Param("endAt")   Instant endAt);



}


