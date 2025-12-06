package com.example.parking.repository;

import com.example.parking.entity.SummaryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryReservationRepository extends JpaRepository<SummaryReservation, Long> {
}


