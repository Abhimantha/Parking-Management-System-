package com.example.parking.repository;

import com.example.parking.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    Optional<Setting> findByConfigKey(String configKey);
    boolean existsByConfigKey(String configKey);
}
