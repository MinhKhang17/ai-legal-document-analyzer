package com.analyzer.api.service.lawyer.impl;

import com.analyzer.api.dto.legalticket.UploadTicketFileRequest;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketFileServiceImplTest {

    @Mock LegalTicketRepository ticketRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @TempDir Path tempDir;

    private TicketFileServiceImpl service;
    private User expert;

    @BeforeEach
    void setUp() {
        service = new TicketFileServiceImpl(ticketRepository, documentRepository, userRepository);
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFileSizeMb", 1L);
        expert = User.builder().id(2L).build();
        LegalTicket ticket = LegalTicket.builder().id("ticket_1").assignedLawyer(expert)
                .status(LegalTicketStatus.IN_REVIEW).build();
        when(ticketRepository.findById("ticket_1")).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expert));
    }

    @Test
    void rejectsOversizedBase64BeforeDecodeAndWrite() {
        byte[] bytes = new byte[1024 * 1024 + 1];
        UploadTicketFileRequest request = request("large.pdf", "application/pdf", bytes);

        assertThatThrownBy(() -> service.uploadFile("ticket_1", 2L, request))
                .isInstanceOf(ConflictException.class).hasMessage("TICKET_FILE_TOO_LARGE");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void rejectsDeclaredPdfWhenMagicBytesDoNotMatch() {
        UploadTicketFileRequest request = request("fake.pdf", "application/pdf",
                "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadFile("ticket_1", 2L, request))
                .isInstanceOf(ConflictException.class).hasMessage("TICKET_FILE_MIME_MISMATCH");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void removesPhysicalFileWhenDatabaseSaveFails() throws Exception {
        UploadTicketFileRequest request = request("valid.pdf", "application/pdf",
                "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII));
        when(documentRepository.save(any())).thenThrow(new RuntimeException("db failed"));

        assertThatThrownBy(() -> service.uploadFile("ticket_1", 2L, request))
                .isInstanceOf(RuntimeException.class).hasMessage("db failed");

        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    private UploadTicketFileRequest request(String name, String mime, byte[] content) {
        return UploadTicketFileRequest.builder().uploadedById(2L).originalFileName(name).fileType(mime)
                .contentBase64(Base64.getEncoder().encodeToString(content)).build();
    }
}
