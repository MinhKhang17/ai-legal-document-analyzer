package com.analyzer.api.repository;

import com.analyzer.api.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByWorkspaceIdAndUserIdAndStatusNotOrderByUploadedAtDesc(
            String workspaceId,
            Long userId,
            String status);

    long countByWorkspaceIdAndUserIdAndStatus(
            String workspaceId,
            Long userId,
            String status);

    long countByWorkspaceIdAndUserIdAndStatusIn(
            String workspaceId,
            Long userId,
            List<String> statuses);

    long countByWorkspaceIdAndUserIdAndStatusNot(
            String workspaceId,
            Long userId,
            String status);

    long countByUserIdAndSourceTypeAndUploadedAtBetween(
            Long userId,
            String sourceType,
            LocalDateTime start,
            LocalDateTime end);

    List<Document> findByLegalTicket_Id(String legalTicketId);

    long countByUserIdAndStatusNot(Long userId, String status);

    @Query("select coalesce(sum(d.fileSize), 0) from Document d where d.user.id = :userId and d.status <> :status")
    long sumFileSizeByUserIdAndStatusNot(@Param("userId") Long userId, @Param("status") String status);
}

