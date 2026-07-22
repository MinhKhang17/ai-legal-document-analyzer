package com.analyzer.api.repository;

import com.analyzer.api.entity.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {
    boolean existsByDedupKey(String dedupKey);
}
