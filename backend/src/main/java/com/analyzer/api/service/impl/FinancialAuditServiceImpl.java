package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.*;
import com.analyzer.api.repository.FinancialAuditLogRepository;
import com.analyzer.api.service.FinancialAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class FinancialAuditServiceImpl implements FinancialAuditService {
    private final FinancialAuditLogRepository repository;
    @Override @Transactional public void record(String action,User actor,String entityType,String entityId,String oldJson,String newJson,String reason,String requestId){ repository.save(FinancialAuditLog.builder().action(action).actor(actor).entityType(entityType).entityId(entityId).oldValuesJson(oldJson).newValuesJson(newJson).reason(reason).requestId(requestId).build()); }
    @Override @Transactional(readOnly=true) public PageResponse<RevenuePayrollDtos.Audit> list(int page,int size){ var p=repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page,Math.min(Math.max(size,1),100))); return PageResponse.<RevenuePayrollDtos.Audit>builder().items(p.map(a->new RevenuePayrollDtos.Audit(a.getId(),a.getAction(),a.getActor()==null?null:a.getActor().getId(),a.getEntityType(),a.getEntityId(),a.getOldValuesJson(),a.getNewValuesJson(),a.getReason(),a.getRequestId(),a.getCreatedAt())).getContent()).page(p.getNumber()).size(p.getSize()).totalItems(p.getTotalElements()).totalPages(p.getTotalPages()).build(); }
}
