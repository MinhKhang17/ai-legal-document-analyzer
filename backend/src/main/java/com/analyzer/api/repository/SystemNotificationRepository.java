package com.analyzer.api.repository;

import com.analyzer.api.entity.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {
    boolean existsByDedupKey(String dedupKey);
}
