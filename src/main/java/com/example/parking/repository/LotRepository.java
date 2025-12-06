package com.example.parking.repository;

import com.example.parking.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, Long> {
    // findAll(), findById(), etc. come from JpaRepository
}
