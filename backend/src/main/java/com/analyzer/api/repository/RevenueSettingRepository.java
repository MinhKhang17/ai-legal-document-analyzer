package com.analyzer.api.repository;

import com.analyzer.api.entity.RevenueSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevenueSettingRepository extends JpaRepository<RevenueSetting, Long> {
}
