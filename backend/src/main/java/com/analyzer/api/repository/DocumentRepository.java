package com.analyzer.api.repository;

import com.analyzer.api.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    List<Document> findByLegalTicket_Id(String legalTicketId);
}

