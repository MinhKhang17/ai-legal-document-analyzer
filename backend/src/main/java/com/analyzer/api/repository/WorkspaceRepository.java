package com.analyzer.api.repository;

import com.analyzer.api.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    Optional<Workspace> findByIdAndUserIdAndStatus(String id, Long userId, String status);
    List<Workspace> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    long countByUserIdAndStatus(Long userId, String status);
    @Query("""
            select count(w) from Workspace w
            where w.user.id = :userId and w.status = :status
              and (w.name <> :excludedName or w.description is null or w.description <> :excludedDescription)
            """)
    long countQuotaWorkspaces(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("excludedName") String excludedName,
            @Param("excludedDescription") String excludedDescription);
    boolean existsByUserIdAndNameIgnoreCaseAndStatus(Long userId, String name, String status);
}
