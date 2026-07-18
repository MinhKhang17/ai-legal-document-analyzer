package com.analyzer.api.repository;

import com.analyzer.api.entity.TicketAttachment;
import com.analyzer.api.enums.TicketAttachmentOwnerType;
import com.analyzer.api.enums.TicketUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, String> {
    List<TicketAttachment> findByIdInAndUploadedBy_IdAndUploadStatus(Collection<String> ids, Long userId, TicketUploadStatus status);
    List<TicketAttachment> findByOwnerTypeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(TicketAttachmentOwnerType ownerType, String ownerId, TicketUploadStatus status);
    long countByOwnerIdAndUploadStatusNot(String ownerId, TicketUploadStatus status);
    long countByTicketIdAndUploadStatusNot(String ticketId, TicketUploadStatus status);
}
