package com.analyzer.api.service.impl;
import com.analyzer.api.service.workspace.impl.WorkspaceServiceImpl;

import com.analyzer.api.dto.workspace.WorkspaceRequest;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.service.notification.EmailService;
import com.analyzer.api.service.subscription.SubscriptionQuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionQuotaService subscriptionQuotaService;
    @Mock EmailService emailService;
    @InjectMocks WorkspaceServiceImpl service;

    @Test
    void duplicateActiveWorkspaceNameGetsExplicitConflictBeforeQuotaConsumption() {
        User user = User.builder().id(7L).build();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(workspaceRepository.existsByUserIdAndNameIgnoreCaseAndStatus(7L, "Matter A", "ACTIVE"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createWorkspace(7L, new WorkspaceRequest(" Matter A ", "description")))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("WORKSPACE_ALREADY_EXISTS");

        verify(subscriptionQuotaService, never()).checkCanCreateWorkspace(user);
        verify(workspaceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void internalAssistantSandboxDoesNotConsumeCustomerWorkspaceQuota() {
        User user = User.builder().id(7L).build();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(workspaceRepository.existsByUserIdAndNameIgnoreCaseAndStatus(
                7L, "Contract Assistant Sandbox", "ACTIVE")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createWorkspace(7L, new WorkspaceRequest(
                "Contract Assistant Sandbox",
                "System workspace for general contract assistant chat"));

        verify(subscriptionQuotaService, never()).checkCanCreateWorkspace(user);
        verify(workspaceRepository).save(any(Workspace.class));
    }
}
