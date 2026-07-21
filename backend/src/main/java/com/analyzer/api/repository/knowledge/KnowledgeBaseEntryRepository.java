package com.analyzer.api.repository.knowledge;

import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.enums.KnowledgeScope;
import com.analyzer.api.enums.KnowledgeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, String> {

    Optional<KnowledgeBaseEntry> findByCode(String code);

    Optional<KnowledgeBaseEntry> findFirstByTitleIgnoreCase(String title);

    List<KnowledgeBaseEntry> findByCurrentStatus(KnowledgeStatus status);

    @Query("""
            SELECT entry
            FROM KnowledgeBaseEntry entry
            WHERE (:keyword IS NULL
                OR LOWER(entry.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(entry.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(entry.category) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR entry.currentStatus = :status)
              AND (:scope IS NULL OR entry.scope = :scope)
              AND (:category IS NULL OR LOWER(entry.category) = LOWER(:category))
              AND (:active IS NULL OR entry.active = :active)
            """)
    Page<KnowledgeBaseEntry> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") KnowledgeStatus status,
            @Param("scope") KnowledgeScope scope,
            @Param("category") String category,
            @Param("active") Boolean active,
            Pageable pageable);
}
