package com.example.parking.service;

import com.example.parking.entity.Report;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.ReportRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReportService {
    private final ReportRepository repo;

    public ReportService(ReportRepository repo) { this.repo = repo; }

    public List<Report> findAll() { return repo.findAll(); }

    public Report findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    public Report create(Report r) { return repo.save(r); }

    public Report update(Long id, Report r) {
        Report existing = findById(id);
        existing.setTitle(r.getTitle());
        existing.setPeriodStart(r.getPeriodStart());
        existing.setPeriodEnd(r.getPeriodEnd());
        existing.setGeneratedAt(r.getGeneratedAt());
        existing.setNotes(r.getNotes());
        return repo.save(existing);
    }

    public void delete(Long id) { repo.deleteById(id); }
}
