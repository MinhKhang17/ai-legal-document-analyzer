package com.analyzer.api.repository;
import com.analyzer.api.entity.ExpertPayoutTransaction; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ExpertPayoutTransactionRepository extends JpaRepository<ExpertPayoutTransaction,String>{
 Optional<ExpertPayoutTransaction> findByEarlyPayoutRequestId(String requestId);
 List<ExpertPayoutTransaction> findByStatementIdOrderByPaidAtAsc(String statementId);
}
