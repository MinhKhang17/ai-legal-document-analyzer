package com.analyzer.api.repository.revenue;

import com.analyzer.api.entity.ExpertPayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface ExpertPayoutTransactionRepository extends JpaRepository<ExpertPayoutTransaction, String> {
    Optional<ExpertPayoutTransaction> findByEarlyPayoutRequestId(String requestId);

    List<ExpertPayoutTransaction> findByStatementIdOrderByPaidAtAsc(String statementId);
}
