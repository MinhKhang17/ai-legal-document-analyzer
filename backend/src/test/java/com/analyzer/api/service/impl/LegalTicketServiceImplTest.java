package com.analyzer.api.service.impl;

import com.analyzer.api.dto.legalticket.CreateLegalTicketRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.mapper.LegalTicketMapper;
import com.analyzer.api.repository.*;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.ExpertRevenueService;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.TicketCollaborationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalTicketServiceImplTest {

    @Mock LegalTicketRepository legalTicketRepository;
    @Mock LegalTicketMessageRepository legalTicketMessageRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatSessionRepository chatSessionRepository;
    @Mock UserRepository userRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock LegalTicketMapper legalTicketMapper;
    @Mock SubscriptionQuotaService subscriptionQuotaService;
    @Mock EmailService emailService;
    @Mock TicketContextSnapshotRepository snapshotRepository;
    @Mock TicketCollaborationService collaborationService;
    @Mock ExpertRevenueService expertRevenueService;
    @Mock AiCitationRepository aiCitationRepository;
    @Mock ExpertTicketCreditReservationRepository expertTicketCreditReservationRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks LegalTicketServiceImpl service;

    @Test
    void repeatedAiTicketRequestReturnsExistingTicketWithoutCreatingAnotherOne() {
        User customer = User.builder().id(7L).firstName("Test").lastName("User")
                .email("test@example.com").password("secret").acceptedTerms(true).build();
        Workspace workspace = Workspace.builder().id("ws-1").user(customer).name("Workspace").status("ACTIVE").build();
        ChatSession session = ChatSession.builder().id("chat-1").user(customer).workspace(workspace).title("Chat").build();
        ChatMessage assistant = ChatMessage.builder().id("msg-1").user(customer).chatSession(session)
                .role(ChatMessageRole.ASSISTANT).requestId("req-1").content("Answer").build();
        LegalTicket existing = LegalTicket.builder().id("ticket-1").requestId("req-1").createdBy(customer)
                .workspace(workspace).question("Question").sharedDocumentIdsJson("[]")
                .sharedProfileFieldsJson("[]").build();
        LegalTicketResponse mapped = new LegalTicketResponse();
        mapped.setId("ticket-1");

        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(subscriptionQuotaService.getCurrentPlan(customer)).thenReturn(new SubscriptionPlan());
        when(workspaceRepository.findById("ws-1")).thenReturn(Optional.of(workspace));
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(assistant));
        when(legalTicketRepository.findByRequestIdAndCreatedByIdAndDeletedFalse("req-1", 7L))
                .thenReturn(Optional.of(existing));
        when(legalTicketMapper.toResponse(existing)).thenReturn(mapped);
        when(collaborationService.enrich(mapped, existing)).thenReturn(mapped);
        when(snapshotRepository.findByTicket_Id("ticket-1")).thenReturn(Optional.empty());

        CreateLegalTicketRequest request = CreateLegalTicketRequest.builder()
                .ticketType(LegalTicketType.CONTACT_EXPERT)
                .workspaceId("ws-1")
                .assistantMessageId("msg-1")
                .question("Question")
                .build();

        LegalTicketResponse result = service.createTicket(7L, request);

        assertThat(result.getId()).isEqualTo("ticket-1");
        verify(legalTicketRepository, never()).save(any());
        verify(legalTicketMessageRepository, never()).save(any());
    }
}
