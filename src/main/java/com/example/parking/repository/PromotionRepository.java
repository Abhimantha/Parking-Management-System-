package com.example.parking.repository;

import com.example.parking.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByActiveTrueOrderByIdAsc();

    Optional<Promotion> findFirstByActiveTrueAndCodeIgnoreCase(String code);
    List<Promotion> findByActiveTrueAndCodeIsNullOrderByIdAsc();
}

