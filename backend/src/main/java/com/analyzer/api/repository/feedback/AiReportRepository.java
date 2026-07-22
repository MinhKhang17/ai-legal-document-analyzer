package com.analyzer.api.repository.feedback;

import com.analyzer.api.entity.AiReport;
import com.analyzer.api.enums.AiReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiReportRepository extends JpaRepository<AiReport, String> {

    List<AiReport> findByStatus(AiReportStatus status);
}
