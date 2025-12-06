package com.example.parking.repository;

import com.example.parking.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LevelRepository extends JpaRepository<Level, Long> {

    // Works with Level.lot.id (keeps your controller call: levels.findByLotId(lotId))
    @Query("select l from Level l where l.lot.id = :lotId")
    List<Level> findByLotId(@Param("lotId") Long lotId);

    // Optional: the derived form if you prefer (not used by your controller)
    List<Level> findByLot_Id(Long lotId);
}
