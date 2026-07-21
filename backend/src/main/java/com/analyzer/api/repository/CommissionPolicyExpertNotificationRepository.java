package com.analyzer.api.repository;
import com.analyzer.api.entity.CommissionPolicyExpertNotification; import com.analyzer.api.enums.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CommissionPolicyExpertNotificationRepository extends JpaRepository<CommissionPolicyExpertNotification,Long>{
 List<CommissionPolicyExpertNotification> findByExpertIdOrderByIdDesc(Long expertId);
 List<CommissionPolicyExpertNotification> findByStatusIn(Collection<NotificationDeliveryStatus> statuses);
 Optional<CommissionPolicyExpertNotification> findByIdAndExpertId(Long id,Long expertId);
}
