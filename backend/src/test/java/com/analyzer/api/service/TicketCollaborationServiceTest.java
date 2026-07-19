package com.analyzer.api.service;

import com.analyzer.api.dto.legalticket.CreateConversationShareRequest;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.ConversationScope;
import com.analyzer.api.enums.TicketUploadStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCollaborationServiceTest {
    @Mock LegalTicketRepository ticketRepository;
    @Mock LegalTicketMessageRepository messageRepository;
    @Mock TicketAttachmentRepository attachmentRepository;
    @Mock ConversationShareRepository shareRepository;
    @Mock TicketAuditLogRepository auditRepository;
    @Mock TicketContextSnapshotRepository snapshotRepository;
    @Mock UserRepository userRepository;
    @Mock LegalTicketMapper mapper;
    @TempDir Path tempDir;
    TicketCollaborationService service;

    @BeforeEach
    void setUp() {
        service = new TicketCollaborationService(ticketRepository, messageRepository, attachmentRepository,
                shareRepository, auditRepository, snapshotRepository, userRepository, mapper);
        ReflectionTestUtils.setField(service, "maxSizeKb", 500L);
        ReflectionTestUtils.setField(service, "maxPerMessage", 5);
        ReflectionTestUtils.setField(service, "maxPerTicket", 30);
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
    }

    @Test
    void rejectsExecutableExtensionBeforeWritingFile() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe",
                "application/octet-stream", new byte[]{1, 2, 3});
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> service.upload(1L, "draft_1", file))
                .isInstanceOf(ConflictException.class).hasMessage("EXECUTABLE_ATTACHMENT_BLOCKED");
        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void rejectsMimeThatDoesNotMatchMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf",
                "application/pdf", "not a pdf".getBytes());
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(attachmentRepository.countByOwnerIdAndUploadStatusNot("draft_1", TicketUploadStatus.REMOVED)).thenReturn(0L);

        assertThatThrownBy(() -> service.upload(1L, "draft_1", file))
                .isInstanceOf(ConflictException.class).hasMessage("ATTACHMENT_SIGNATURE_INVALID");
    }

    @Test
    void sharePersistsOnlyTokenHashAndDefaultsToSevenDays() {
        User owner = User.builder().id(1L).build();
        LegalTicket ticket = LegalTicket.builder().id("ticket_1").createdBy(owner)
                .relatedChatSessionId("chat_1").conversationScope(ConversationScope.SELECTED_RESPONSE).build();
        when(ticketRepository.findByIdAndDeletedFalse("ticket_1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(shareRepository.save(any())).thenAnswer(invocation -> {
            ConversationShare share = invocation.getArgument(0);
            share.setId("share_1"); share.setCreatedAt(LocalDateTime.now()); return share;
        });

        var response = service.createShare(1L, "CUSTOMER", "ticket_1", new CreateConversationShareRequest());

        ArgumentCaptor<ConversationShare> captor = ArgumentCaptor.forClass(ConversationShare.class);
        verify(shareRepository).save(captor.capture());
        assertThat(captor.getValue().getShareTokenHash()).hasSize(64);
        assertThat(response.getShareUrl()).doesNotContain(captor.getValue().getShareTokenHash());
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }
}
