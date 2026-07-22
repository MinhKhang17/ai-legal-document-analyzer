package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.legalticket.*;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.legalticket.LegalTicketMessageRepository;
import com.analyzer.api.repository.legalticket.TicketAttachmentRepository;
import com.analyzer.api.repository.legalticket.TicketAuditLogRepository;
import com.analyzer.api.repository.legalticket.TicketContextSnapshotRepository;
import com.analyzer.api.repository.legalticket.ConversationShareRepository;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.RevenuePayrollService;
import com.analyzer.api.service.TicketCollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class TicketCollaborationServiceImpl implements TicketCollaborationService {
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "jar", "apk", "msi");
    private static final List<String> DEFAULT_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain");

    private final LegalTicketRepository ticketRepository;
    private final LegalTicketMessageRepository messageRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final ConversationShareRepository shareRepository;
    private final TicketAuditLogRepository auditRepository;
    private final TicketContextSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final LegalTicketMapper ticketMapper;
    private final RevenuePayrollService revenuePayrollService;

    @org.springframework.beans.factory.annotation.Autowired
    public TicketCollaborationServiceImpl(LegalTicketRepository ticketRepository,LegalTicketMessageRepository messageRepository,
        TicketAttachmentRepository attachmentRepository,ConversationShareRepository shareRepository,TicketAuditLogRepository auditRepository,
        TicketContextSnapshotRepository snapshotRepository,UserRepository userRepository,LegalTicketMapper ticketMapper,RevenuePayrollService revenuePayrollService){
        this.ticketRepository=ticketRepository;this.messageRepository=messageRepository;this.attachmentRepository=attachmentRepository;
        this.shareRepository=shareRepository;this.auditRepository=auditRepository;this.snapshotRepository=snapshotRepository;
        this.userRepository=userRepository;this.ticketMapper=ticketMapper;this.revenuePayrollService=revenuePayrollService;
    }
    /** Compatibility constructor retained for existing tests. */
    public TicketCollaborationServiceImpl(LegalTicketRepository ticketRepository,LegalTicketMessageRepository messageRepository,
        TicketAttachmentRepository attachmentRepository,ConversationShareRepository shareRepository,TicketAuditLogRepository auditRepository,
        TicketContextSnapshotRepository snapshotRepository,UserRepository userRepository,LegalTicketMapper ticketMapper){
        this(ticketRepository,messageRepository,attachmentRepository,shareRepository,auditRepository,snapshotRepository,userRepository,ticketMapper,null);
    }

    @Value("${app.ticket-attachments.max-size-kb:500}")
    private long maxSizeKb;
    @Value("${app.ticket-attachments.max-per-message:5}")
    private int maxPerMessage;
    @Value("${app.ticket-attachments.max-per-ticket:30}")
    private int maxPerTicket;
    @Value("${app.ticket-attachments.upload-root:uploads/ticket_attachments}")
    private String uploadRoot;
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public AttachmentPolicyResponse policy() {
        return AttachmentPolicyResponse.builder().maxAttachmentSizeKb(maxSizeKb)
                .maxAttachmentsPerMessage(maxPerMessage).maxAttachmentsPerTicket(maxPerTicket)
                .allowedMimeTypes(DEFAULT_MIME_TYPES).build();
    }

    @Transactional
    public TicketAttachmentResponse upload(Long userId, String ownerId, MultipartFile file) {
        User user = requireUser(userId);
        if (file == null || file.isEmpty())
            throw new ConflictException("ATTACHMENT_EMPTY");
        if (file.getSize() > maxSizeKb * 1024L)
            throw new ConflictException("ATTACHMENT_TOO_LARGE");
        String originalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(originalName);
        if (BLOCKED_EXTENSIONS.contains(extension))
            throw new ConflictException("EXECUTABLE_ATTACHMENT_BLOCKED");
        if (ownerId == null || ownerId.isBlank())
            ownerId = "draft_" + UUID.randomUUID().toString().replace("-", "");
        long existing = ownerId.startsWith("ticket_")
                ? attachmentRepository.countByTicketIdAndUploadStatusNot(ownerId, TicketUploadStatus.REMOVED)
                : attachmentRepository.countByOwnerIdAndUploadStatusNot(ownerId, TicketUploadStatus.REMOVED);
        if (existing >= maxPerTicket)
            throw new ConflictException("ATTACHMENT_TICKET_LIMIT_EXCEEDED");
        try {
            byte[] bytes = file.getBytes();
            String detectedMime = detectMime(bytes, extension);
            if (!DEFAULT_MIME_TYPES.contains(detectedMime))
                throw new ConflictException("ATTACHMENT_MIME_NOT_ALLOWED");
            String declared = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
            if (!declared.isBlank() && !declared.equals("application/octet-stream") && !declared.equals(detectedMime)) {
                throw new ConflictException("ATTACHMENT_MIME_MISMATCH");
            }
            String id = "attachment_" + UUID.randomUUID().toString().replace("-", "");
            String storageKey = LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID().toString().replace("-", "");
            String storedName = id + (extension.isBlank() ? "" : "." + extension);
            Path root = Path.of(uploadRoot).toAbsolutePath().normalize();
            Path directory = root.resolve(storageKey).normalize();
            if (!directory.startsWith(root))
                throw new ConflictException("INVALID_STORAGE_PATH");
            Files.createDirectories(directory);
            Files.write(directory.resolve(storedName), bytes, StandardOpenOption.CREATE_NEW);
            TicketAttachment saved = attachmentRepository.save(TicketAttachment.builder()
                    .id(id).ownerType(TicketAttachmentOwnerType.TICKET_DRAFT).ownerId(ownerId)
                    .ticketId(ownerId.startsWith("ticket_") ? ownerId : null)
                    .uploadedBy(user).originalFileName(originalName).storedFileName(storedName)
                    .mimeType(detectedMime).sizeBytes((long) bytes.length).storageKey(storageKey)
                    .checksum(sha256(bytes)).scanStatus(AttachmentScanStatus.NOT_CONFIGURED)
                    .uploadStatus(TicketUploadStatus.UPLOADED).build());
            return toAttachment(saved);
        } catch (IOException ex) {
            throw new ConflictException("ATTACHMENT_UPLOAD_FAILED");
        }
    }

    @Transactional
    public void remove(Long userId, String attachmentId) {
        TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTACHMENT_NOT_FOUND"));
        if (!attachment.getUploadedBy().getId().equals(userId))
            throw new ForbiddenException("ATTACHMENT_ACCESS_DENIED");
        attachment.setUploadStatus(TicketUploadStatus.REMOVED);
        attachment.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public LegalTicketMessageResponse addMessage(Long userId, String role, String ticketId,
            CreateTicketMessageRequest request) {
        LegalTicket ticket = requireParticipant(userId, role, ticketId);
        String clientMessageId = normalizeClientMessageId(request.getClientMessageId());
        if (clientMessageId != null) {
            Optional<LegalTicketMessage> existing = messageRepository
                    .findByTicket_IdAndSender_IdAndClientMessageId(ticketId, userId, clientMessageId);
            if (existing.isPresent())
                return toMessage(existing.get());
        }
        List<String> ids = request.getAttachmentIds() == null ? List.of()
                : request.getAttachmentIds().stream().distinct().toList();
        if (ids.size() > maxPerMessage)
            throw new ConflictException("ATTACHMENT_MESSAGE_LIMIT_EXCEEDED");
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank() && ids.isEmpty())
            throw new ConflictException("MESSAGE_CONTENT_OR_ATTACHMENT_REQUIRED");
        List<TicketAttachment> attachments = claimAttachments(ids, userId, ticketId, null);
        LegalTicketMessage replyTo = null;
        if (request.getReplyToMessageId() != null && !request.getReplyToMessageId().isBlank()) {
            replyTo = messageRepository.findById(request.getReplyToMessageId())
                    .filter(message -> message.getTicket().getId().equals(ticketId))
                    .orElseThrow(() -> new ResourceNotFoundException("REPLY_MESSAGE_NOT_FOUND"));
        }
        User sender = requireUser(userId);
        LegalTicketMessage message = messageRepository.save(LegalTicketMessage.builder()
                .ticket(ticket).sender(sender).senderRole(role.toUpperCase(Locale.ROOT))
                .clientMessageId(clientMessageId)
                .content(content).replyToMessage(replyTo).messageType(messageType(role))
                .internalOnly(false).build());
        attachments.forEach(item -> {
            item.setOwnerType(TicketAttachmentOwnerType.TICKET_MESSAGE);
            item.setOwnerId(message.getId());
            item.setTicketId(ticketId);
        });
        audit(ticket, sender, "TICKET_MESSAGE_ADDED", "{\"messageId\":\"" + message.getId() + "\"}");
        return toMessage(message);
    }

    @Transactional(readOnly = true)
    public PageResponse<LegalTicketMessageResponse> messages(Long userId, String role, String ticketId, int page,
            int size) {
        requireParticipant(userId, role, ticketId);
        var result = messageRepository.findByTicket_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
                ticketId, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
        List<LegalTicketMessageResponse> items = new ArrayList<>(
                result.getContent().stream().map(this::toMessage).toList());
        Collections.reverse(items);
        return PageResponse.<LegalTicketMessageResponse>builder().items(items).page(result.getNumber())
                .size(result.getSize()).totalItems(result.getTotalElements()).totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public ConversationShareResponse createShare(Long userId, String role, String ticketId,
            CreateConversationShareRequest request) {
        LegalTicket ticket = requireParticipant(userId, role, ticketId);
        if (!(ticket.getCreatedBy().getId().equals(userId) || "ADMIN".equalsIgnoreCase(role))) {
            throw new ForbiddenException("SHARE_MANAGEMENT_DENIED");
        }
        if (ticket.getRelatedChatSessionId() == null || ticket.getRelatedChatSessionId().isBlank()) {
            throw new ConflictException("TICKET_HAS_NO_CONVERSATION");
        }
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        LocalDateTime expiresAt = request.getExpiresAt() != null ? request.getExpiresAt()
                : LocalDateTime.now().plusDays(7);
        if (expiresAt.isAfter(LocalDateTime.now().plusDays(30)))
            throw new ConflictException("SHARE_EXPIRY_TOO_LONG");
        ConversationShare share = shareRepository.save(ConversationShare.builder().ticket(ticket)
                .sessionId(ticket.getRelatedChatSessionId())
                .shareTokenHash(sha256(token.getBytes(StandardCharsets.UTF_8)))
                .shareScope(request.getScope() != null ? request.getScope() : ticket.getConversationScope())
                .accessMode(ConversationShareAccessMode.PARTICIPANTS_ONLY).expiresAt(expiresAt)
                .createdBy(requireUser(userId)).build());
        audit(ticket, share.getCreatedBy(), "CONVERSATION_SHARE_CREATED", "{\"shareId\":\"" + share.getId() + "\"}");
        return toShare(share, frontendBaseUrl + "/shared-conversation/" + token);
    }

    @Transactional
    public void revokeShare(Long userId, String role, String ticketId, String shareId) {
        LegalTicket ticket = requireParticipant(userId, role, ticketId);
        if (!(ticket.getCreatedBy().getId().equals(userId) || "ADMIN".equalsIgnoreCase(role)))
            throw new ForbiddenException("SHARE_MANAGEMENT_DENIED");
        ConversationShare share = shareRepository.findByIdAndTicket_Id(shareId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("SHARE_NOT_FOUND"));
        share.setRevokedAt(LocalDateTime.now());
        audit(ticket, requireUser(userId), "CONVERSATION_SHARE_REVOKED", "{\"shareId\":\"" + shareId + "\"}");
    }

    @Transactional
    public LegalTicketResponse openShare(Long userId, String role, String token) {
        ConversationShare share = shareRepository.findByShareTokenHash(sha256(token.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new ResourceNotFoundException("SHARE_NOT_FOUND"));
        if (share.getRevokedAt() != null || !share.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new ForbiddenException("SHARE_EXPIRED_OR_REVOKED");
        LegalTicket ticket = requireParticipant(userId, role, share.getTicket().getId());
        audit(ticket, requireUser(userId), "CONVERSATION_SHARE_VIEWED", "{\"shareId\":\"" + share.getId() + "\"}");
        return enrich(ticketMapper.toResponse(ticket), ticket);
    }

    @Transactional
    public Resource download(Long userId, String role, String attachmentId) {
        TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTACHMENT_NOT_FOUND"));
        String ticketId = resolveAttachmentTicketId(attachment);
        LegalTicket ticket = requireParticipant(userId, role, ticketId);
        try {
            Path root = Path.of(uploadRoot).toAbsolutePath().normalize();
            Path file = root.resolve(attachment.getStorageKey()).resolve(attachment.getStoredFileName()).normalize();
            if (!file.startsWith(root))
                throw new ForbiddenException("ATTACHMENT_ACCESS_DENIED");
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable())
                throw new ResourceNotFoundException("ATTACHMENT_NOT_FOUND");
            audit(ticket, requireUser(userId), "TICKET_ATTACHMENT_DOWNLOADED",
                    "{\"attachmentId\":\"" + attachmentId + "\"}");
            return resource;
        } catch (IOException ex) {
            throw new ResourceNotFoundException("ATTACHMENT_NOT_FOUND");
        }
    }

    public String attachmentFilename(String attachmentId) {
        return attachmentRepository.findById(attachmentId).map(TicketAttachment::getOriginalFileName)
                .orElse("attachment");
    }

    @Transactional
    public void claimForTicket(List<String> ids, Long userId, String ticketId) {
        claimAttachments(ids, userId, ticketId, TicketAttachmentOwnerType.TICKET);
    }

    @Transactional
    public void auditTicket(LegalTicket ticket, User actor, String action, String metadataJson) {
        audit(ticket, actor, action, metadataJson);
    }

    @Transactional
    public LegalTicketResponse transition(Long userId, String role, String ticketId, TicketActionRequest request) {
        LegalTicket ticket = requireParticipant(userId, role, ticketId);
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        LegalTicketStatus current = ticket.getStatus();
        LegalTicketStatus next;
        switch (action) {
            case "START_REVIEW" -> {
                if (!"EXPERT".equalsIgnoreCase(role)
                        || !Set.of(LegalTicketStatus.ASSIGNED_TO_EXPERT, LegalTicketStatus.ASSIGNED_TO_LAWYER,
                                LegalTicketStatus.REOPENED, LegalTicketStatus.CUSTOMER_RESPONDED).contains(current))
                    invalidTransition();
                if (ticket.getPricingType() == TicketPricingType.PAID
                        && ticket.getCustomerPaymentStatus() != TicketPaymentStatus.PAID)
                    throw new ConflictException("PAYMENT_REQUIRED_BEFORE_EXPERT_START");
                next = LegalTicketStatus.IN_REVIEW;
                if (ticket.getStartedAt() == null) ticket.setStartedAt(LocalDateTime.now());
            }
            case "REQUEST_USER_INFO" -> {
                if (!("EXPERT".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))
                        || !Set.of(LegalTicketStatus.ASSIGNED, LegalTicketStatus.ASSIGNED_TO_LAWYER,
                                LegalTicketStatus.IN_REVIEW).contains(current))
                    invalidTransition();
                next = LegalTicketStatus.WAITING_FOR_USER;
            }
            case "USER_REPLIED" -> {
                if (!"CUSTOMER".equalsIgnoreCase(role) || !Set
                        .of(LegalTicketStatus.WAITING_FOR_USER, LegalTicketStatus.NEED_MORE_INFO).contains(current))
                    invalidTransition();
                next = LegalTicketStatus.WAITING_FOR_EXPERT;
            }
            case "RESOLVE" -> {
                throw new ConflictException("FINAL_DELIVERABLE_REQUIRED_USE_EXPERT_RESOLVE_ENDPOINT");
            }
            case "CLOSE" -> {
                if (!"CUSTOMER".equalsIgnoreCase(role)
                        || current != LegalTicketStatus.RESOLVED)
                    invalidTransition();
                next = LegalTicketStatus.CLOSED;
                ticket.setClosedAt(LocalDateTime.now());
            }
            case "REOPEN" -> {
                if (!("CUSTOMER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))
                        || !Set.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED).contains(current))
                    invalidTransition();
                next = LegalTicketStatus.REOPENED;
                ticket.setReopenedAt(LocalDateTime.now());
            }
            default -> throw new ConflictException("UNKNOWN_TICKET_ACTION");
        }
        ticket.setStatus(next);
        audit(ticket, requireUser(userId), "TICKET_STATUS_CHANGED",
                "{\"from\":\"" + current + "\",\"to\":\"" + next + "\"}");
        return enrich(ticketMapper.toResponse(ticket), ticket);
    }

    private void invalidTransition() {
        throw new ConflictException("INVALID_STATUS_TRANSITION");
    }

    public LegalTicketResponse enrich(LegalTicketResponse response, LegalTicket ticket) {
        response.setSharedDocumentIds(parseIds(ticket.getSharedDocumentIdsJson()));
        snapshotRepository.findByTicket_Id(ticket.getId()).ifPresent(snapshot -> response.setContextSnapshot(
                TicketContextSnapshotResponse.builder().id(snapshot.getId()).userQuestion(snapshot.getUserQuestion())
                        .assistantAnswer(snapshot.getAssistantAnswer())
                        .conversationTitle(snapshot.getConversationTitle())
                        .citationSnapshotJson(snapshot.getCitationSnapshotJson())
                        .documentSnapshotJson(snapshot.getDocumentSnapshotJson())
                        .selectedMessageSnapshotJson(snapshot.getSelectedMessageSnapshotJson())
                        .contentHash(snapshot.getContentHash()).createdAt(snapshot.getCreatedAt()).build()));
        return response;
    }

    private List<TicketAttachment> claimAttachments(List<String> ids, Long userId, String ownerId,
            TicketAttachmentOwnerType type) {
        if (ids == null || ids.isEmpty())
            return List.of();
        List<TicketAttachment> attachments = attachmentRepository.findByIdInAndUploadedBy_IdAndUploadStatus(ids, userId,
                TicketUploadStatus.UPLOADED);
        if (attachments.size() != ids.size())
            throw new ForbiddenException("ATTACHMENT_ACCESS_DENIED");
        boolean invalidOwner = attachments.stream()
                .anyMatch(item -> item.getOwnerType() != TicketAttachmentOwnerType.TICKET_DRAFT
                        || (item.getTicketId() != null && !item.getTicketId().equals(ownerId)));
        if (invalidOwner)
            throw new ConflictException("ATTACHMENT_ALREADY_ASSIGNED_OR_WRONG_TICKET");
        if (type != null)
            attachments.forEach(item -> {
                item.setOwnerType(type);
                item.setOwnerId(ownerId);
                item.setTicketId(ownerId);
            });
        return attachments;
    }

    private LegalTicket requireParticipant(Long userId, String role, String ticketId) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));
        boolean allowed = "ADMIN".equalsIgnoreCase(role)
                || ("CUSTOMER".equalsIgnoreCase(role) && ticket.getCreatedBy().getId().equals(userId))
                || ("EXPERT".equalsIgnoreCase(role) && ticket.getAssignedLawyer() != null
                        && ticket.getAssignedLawyer().getId().equals(userId));
        if (!allowed)
            throw new ForbiddenException("TICKET_ACCESS_DENIED");
        return ticket;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
    }

    private LegalTicketMessageType messageType(String role) {
        return "EXPERT".equalsIgnoreCase(role) ? LegalTicketMessageType.EXPERT_RESPONSE
                : "ADMIN".equalsIgnoreCase(role) ? LegalTicketMessageType.ADMIN_NOTE
                        : LegalTicketMessageType.CUSTOMER_REPLY;
    }

    private String normalizeClientMessageId(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.trim();
    }

    private TicketAttachmentResponse toAttachment(TicketAttachment a) {
        return TicketAttachmentResponse.builder().id(a.getId()).originalFileName(a.getOriginalFileName())
                .mimeType(a.getMimeType()).sizeBytes(a.getSizeBytes()).scanStatus(a.getScanStatus().name())
                .uploadStatus(a.getUploadStatus().name()).createdAt(a.getCreatedAt())
                .downloadUrl("/api/attachments/" + a.getId() + "/download").build();
    }

    private LegalTicketMessageResponse toMessage(LegalTicketMessage m) {
        LegalTicketMessageResponse response = ticketMapper.toMessageResponse(m);
        response.setReplyToMessageId(m.getReplyToMessage() != null ? m.getReplyToMessage().getId() : null);
        response.setEditedAt(m.getEditedAt());
        response.setAttachments(
                attachmentRepository
                        .findByOwnerTypeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
                                TicketAttachmentOwnerType.TICKET_MESSAGE, m.getId(), TicketUploadStatus.UPLOADED)
                        .stream().map(this::toAttachment).toList());
        return response;
    }

    private ConversationShareResponse toShare(ConversationShare s, String url) {
        return ConversationShareResponse.builder().id(s.getId()).ticketId(s.getTicket().getId()).shareUrl(url)
                .scope(s.getShareScope().name()).accessMode(s.getAccessMode().name()).expiresAt(s.getExpiresAt())
                .revokedAt(s.getRevokedAt()).createdAt(s.getCreatedAt()).build();
    }

    private void audit(LegalTicket t, User actor, String action, String metadata) {
        auditRepository.save(TicketAuditLog.builder().ticketId(t.getId()).actor(actor)
                .actorType(actor == null ? "SYSTEM" : "USER").action(action)
                .metadataJson(metadata).build());
    }

    private String resolveAttachmentTicketId(TicketAttachment a) {
        if (a.getOwnerType() == TicketAttachmentOwnerType.TICKET)
            return a.getOwnerId();
        if (a.getOwnerType() == TicketAttachmentOwnerType.TICKET_MESSAGE)
            return messageRepository.findById(a.getOwnerId()).map(m -> m.getTicket().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND"));
        throw new ForbiddenException("ATTACHMENT_NOT_ASSIGNED");
    }

    private String sanitizeFilename(String raw) {
        String name = Optional.ofNullable(raw).orElse("attachment").replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (name.isBlank() || name.equals(".") || name.equals(".."))
            name = "attachment";
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String detectMime(byte[] b, String ext) {
        if (starts(b, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }) && Set.of("jpg", "jpeg").contains(ext))
            return "image/jpeg";
        if (starts(b, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }) && "png".equals(ext))
            return "image/png";
        if (b.length > 12 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP") && "webp".equals(ext))
            return "image/webp";
        if (starts(b, "%PDF".getBytes(StandardCharsets.US_ASCII)) && "pdf".equals(ext))
            return "application/pdf";
        if (starts(b, new byte[] { 0x50, 0x4B }) && "docx".equals(ext) && isDocx(b))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if ("txt".equals(ext) && !containsNull(b))
            return "text/plain";
        throw new ConflictException("ATTACHMENT_SIGNATURE_INVALID");
    }

    private boolean isDocx(byte[] b) {
        boolean content = false, word = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(b))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("[Content_Types].xml"))
                    content = true;
                if (e.getName().startsWith("word/"))
                    word = true;
                if (content && word)
                    return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private boolean containsNull(byte[] b) {
        for (byte value : b)
            if (value == 0)
                return true;
        return false;
    }

    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length)
            return false;
        for (int i = 0; i < prefix.length; i++)
            if (value[i] != prefix[i])
                return false;
        return true;
    }

    private byte[] randomBytes(int size) {
        byte[] b = new byte[size];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> parseIds(String json) {
        if (json == null || json.isBlank())
            return List.of();
        return Arrays.stream(json.replaceAll("[\\[\\]\" ]", "").split(",")).filter(s -> !s.isBlank()).toList();
    }
}
