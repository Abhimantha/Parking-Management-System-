package com.example.parking.service;

import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.SlotStatus;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.SlotRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SlotService {
    private final SlotRepository repo;

    public SlotService(SlotRepository repo) { this.repo = repo; }

    public List<Slot> findAll() { return repo.findAll(); }

    public Slot findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + id));
    }

    public Slot create(Slot s) {
        if (repo.existsBySlotNumber(s.getSlotNumber())) {
            throw new IllegalArgumentException("Slot number already exists: " + s.getSlotNumber());
        }
        return repo.save(s);
    }

    public Slot update(Long id, Slot s) {
        Slot existing = findById(id);
        existing.setSlotNumber(s.getSlotNumber());
        existing.setLevel(s.getLevel());
        existing.setType(s.getType());
        existing.setStatus(s.getStatus());
        return repo.save(existing);
    }

    public Slot updateStatus(Long id, SlotStatus status) {
        Slot existing = findById(id);
        existing.setStatus(status);
        return repo.save(existing);
    }

    public void delete(Long id) { repo.deleteById(id); }
}
