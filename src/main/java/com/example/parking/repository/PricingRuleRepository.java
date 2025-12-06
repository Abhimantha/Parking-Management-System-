package com.example.parking.repository;

import com.example.parking.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    List<PricingRule> findByActiveTrueOrderByPriorityAscIdAsc();

    List<PricingRule> findByActiveTrueAndRuleTypeOrderByPriorityAscIdAsc(String ruleType);
}

