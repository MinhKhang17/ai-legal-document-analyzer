package com.analyzer.api.service.feedback;

import com.analyzer.api.dto.feedback.AiReportCreateRequest;
import com.analyzer.api.dto.feedback.AiReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiReportService {

    AiReportResponse create(AiReportCreateRequest request);

    AiReportResponse getById(String id);

    Page<AiReportResponse> getAll(Pageable pageable);
}
