package com.analyzer.api.entity;

import com.analyzer.api.enums.AttachmentScanStatus;
import com.analyzer.api.enums.TicketAttachmentOwnerType;
import com.analyzer.api.enums.TicketUploadStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachments", indexes = {
        @Index(name = "idx_ticket_attachments_owner", columnList = "owner_type,owner_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketAttachment {
    @Id private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private TicketAttachmentOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "ticket_id")
    private String ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;
    @Column(name = "stored_file_name", nullable = false)
    private String storedFileName;
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false)
    private AttachmentScanStatus scanStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false)
    private TicketUploadStatus uploadStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = "attachment_" + UUID.randomUUID().toString().replace("-", "");
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (scanStatus == null) scanStatus = AttachmentScanStatus.NOT_CONFIGURED;
        if (uploadStatus == null) uploadStatus = TicketUploadStatus.UPLOADED;
    }
}
