package com.example.parking.service;

import com.example.parking.entity.Setting;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.SettingRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SettingService {
    private final SettingRepository repo;

    public SettingService(SettingRepository repo) { this.repo = repo; }

    public List<Setting> findAll() { return repo.findAll(); }

    public Setting findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + id));
    }

    public Setting findByKey(String key) {
        return repo.findByConfigKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Setting key not found: " + key));
    }

    public Setting create(Setting s) {
        if (repo.existsByConfigKey(s.getConfigKey())) {
            throw new IllegalArgumentException("Duplicate setting key: " + s.getConfigKey());
        }
        return repo.save(s);
    }

    public Setting update(Long id, Setting s) {
        Setting existing = findById(id);
        existing.setConfigKey(s.getConfigKey());
        existing.setConfigValue(s.getConfigValue());
        existing.setDescription(s.getDescription());
        return repo.save(existing);
    }

    public void delete(Long id) { repo.deleteById(id); }
}
