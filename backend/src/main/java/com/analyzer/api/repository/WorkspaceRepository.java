package com.analyzer.api.repository;

import com.analyzer.api.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    Optional<Workspace> findByIdAndUserIdAndStatus(String id, Long userId, String status);
    List<Workspace> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    long countByUserIdAndStatus(Long userId, String status);
}
