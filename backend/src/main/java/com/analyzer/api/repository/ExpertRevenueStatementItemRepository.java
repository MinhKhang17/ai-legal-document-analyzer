package com.analyzer.api.repository;
import com.analyzer.api.entity.ExpertRevenueStatementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ExpertRevenueStatementItemRepository extends JpaRepository<ExpertRevenueStatementItem,String>{
 boolean existsByTicketId(String ticketId); Optional<ExpertRevenueStatementItem> findByTicketId(String ticketId);
 List<ExpertRevenueStatementItem> findByStatementIdOrderByRecognizedAtAsc(String statementId);
}
