package com.example.parking.service;

import com.example.parking.entity.*;
import com.example.parking.entity.enums.ReservationStatus;
import com.example.parking.entity.enums.SlotStatus;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ReservationService {
    private final ReservationRepository repo;
    private final UserService userService;
    private final SlotService slotService;

    public ReservationService(ReservationRepository repo, UserService userService, SlotService slotService) {
        this.repo = repo;
        this.userService = userService;
        this.slotService = slotService;
    }

    public List<Reservation> findAll() { return repo.findAll(); }

    public Reservation findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    @Transactional
    public Reservation create(Reservation r, Long userId, Long slotId) {
        User user = userService.findById(userId);
        Slot slot = slotService.findById(slotId);
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalArgumentException("Slot is not available: " + slot.getSlotNumber());
        }
        r.setUser(user);
        r.setSlot(slot);
        slot.setStatus(SlotStatus.RESERVED); // lock slot
        return repo.save(r);
    }

    @Transactional
    public Reservation update(Long id, Reservation r, Long userId, Long slotId) {
        Reservation existing = findById(id);
        if (userId != null) existing.setUser(userService.findById(userId));
        if (slotId != null) {
            Slot oldSlot = existing.getSlot();
            if (oldSlot.getId().longValue() != slotId.longValue()) {
                // free old, reserve new
                oldSlot.setStatus(SlotStatus.AVAILABLE);
                Slot newSlot = slotService.findById(slotId);
                if (newSlot.getStatus() != SlotStatus.AVAILABLE)
                    throw new IllegalArgumentException("New slot not available: " + newSlot.getSlotNumber());
                newSlot.setStatus(SlotStatus.RESERVED);
                existing.setSlot(newSlot);
            }
        }
        existing.setStartTime(r.getStartTime());
        existing.setEndTime(r.getEndTime());
        existing.setStatus(r.getStatus());
        return repo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Reservation existing = findById(id);
        existing.getSlot().setStatus(SlotStatus.AVAILABLE);
        repo.deleteById(id);
    }

    @Transactional
    public Reservation updateStatus(Long id, ReservationStatus status) {
        Reservation existing = findById(id);
        existing.setStatus(status);
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED) {
            existing.getSlot().setStatus(SlotStatus.AVAILABLE);
        }
        return repo.save(existing);
    }

    public List<Reservation> findAllByUserId(Long userId) {
        return repo.findByUser_Id(userId);
    }

}
