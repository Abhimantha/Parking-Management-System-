package com.example.parking.controller;

import com.example.parking.entity.Slot;
import com.example.parking.entity.enums.ReservationStatus;
import com.example.parking.entity.enums.SlotStatus;
import com.example.parking.entity.enums.SlotType;
import com.example.parking.repository.ReservationRepository;
import com.example.parking.repository.SlotRepository;
import com.example.parking.service.SlotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService service;
    private final SlotRepository slotRepo;
    private final ReservationRepository reservationRepo;

    public SlotController(SlotService service, SlotRepository slotRepo, ReservationRepository reservationRepo) {
        this.service = service;
        this.slotRepo = slotRepo;
        this.reservationRepo = reservationRepo;
    }

    // --------- RAW LIST (unchanged: returns DB status) ----------
    @GetMapping
    public List<Slot> list(@RequestParam(required = false) String lot,
                           @RequestParam(required = false) String level) {
        if (lot != null && level != null) return slotRepo.findByLotAndLevelOrderByMapRowAscMapColAsc(lot, level);
        if (lot != null)                    return slotRepo.findByLotOrderByMapRowAscMapColAsc(lot);
        if (level != null)                  return slotRepo.findByLevelOrderByMapRowAscMapColAsc(level);
        return service.findAll();
    }

    // --------- READ (single) ----------
    @GetMapping("/{id}")
    public Slot one(@PathVariable Long id) { return service.findById(id); }

    // --------- CREATE ----------
    @PostMapping
    public ResponseEntity<Slot> create(@RequestBody Slot s) {
        return ResponseEntity.ok(service.create(s));
    }

    // --------- UPDATE ----------
    @PutMapping("/{id}")
    public Slot update(@PathVariable Long id, @RequestBody Slot s) {
        return service.update(id, s);
    }

    // --------- PATCH status ----------
    @PatchMapping("/{id}/status")
    public Slot updateStatus(@PathVariable Long id, @RequestParam SlotStatus status) {
        return service.updateStatus(id, status);
    }

    // --------- DELETE ----------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --------- NEW: computed view for Slots page ----------
    // Show “RESERVED” if ANY reservation is ACTIVE for that slot. COMPLETED/CANCELLED -> AVAILABLE.
    // MAINTENANCE stays MAINTENANCE.
    @GetMapping("/current")
    public List<SlotView> listWithCurrentStatus(@RequestParam(required = false) String lot,
                                                @RequestParam(required = false) String level) {
        final List<Slot> slots =
                (lot != null && level != null) ? slotRepo.findByLotAndLevelOrderByMapRowAscMapColAsc(lot, level) :
                        (lot != null)                  ? slotRepo.findByLotOrderByMapRowAscMapColAsc(lot) :
                                (level != null)                ? slotRepo.findByLevelOrderByMapRowAscMapColAsc(level) :
                                        service.findAll();

        return slots.stream().map(s -> {
            // default to DB status
            SlotStatus effective = s.getStatus();

            if (effective != SlotStatus.MAINTENANCE) {
                // time-agnostic rule: ACTIVE => RESERVED, else AVAILABLE
                boolean anyActive = reservationRepo.existsBySlot_IdAndStatus(s.getId(), ReservationStatus.ACTIVE);
                effective = anyActive ? SlotStatus.RESERVED : SlotStatus.AVAILABLE;

                // If you prefer time-aware behavior, swap to:
                // boolean anyActive = reservationRepo.hasActiveOrOpenEnded(s.getId(), List.of(ReservationStatus.ACTIVE), LocalDateTime.now());
            }

            return new SlotView(
                    s.getId(),
                    s.getSlotNumber(),
                    s.getLevel(),
                    s.getType(),
                    effective.name(),                 // computed for display
                    s.getStatus().name(),             // DB status for editing
                    s.getLot(),
                    s.getMapRow(),
                    s.getMapCol()
            );
        }).toList();
    }

    // DTO for /api/slots/current
    public static final class SlotView {
        public final Long id;
        public final String slotNumber;
        public final String level;
        public final SlotType type;
        public final String status;   // computed: AVAILABLE | RESERVED | MAINTENANCE
        public final String dbStatus; // stored DB value to prefill the edit modal safely
        public final String lot;
        public final Integer mapRow;
        public final Integer mapCol;

        public SlotView(Long id, String slotNumber, String level, SlotType type,
                        String status, String dbStatus, String lot, Integer mapRow, Integer mapCol) {
            this.id = id;
            this.slotNumber = slotNumber;
            this.level = level;
            this.type = type;
            this.status = status;
            this.dbStatus = dbStatus;
            this.lot = lot;
            this.mapRow = mapRow;
            this.mapCol = mapCol;
        }
    }
}
