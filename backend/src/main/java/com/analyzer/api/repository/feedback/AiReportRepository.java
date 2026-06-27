package com.analyzer.api.repository.feedback;

import com.analyzer.api.entity.AiReport;
import com.analyzer.api.enums.AiReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiReportRepository extends JpaRepository<AiReport, String> {

    List<AiReport> findByStatus(AiReportStatus status);
}
