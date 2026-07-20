package com.analyzer.api.service.admin;

import com.analyzer.api.dto.legalticket.AdminChatHistoryResponse;
import com.analyzer.api.dto.legalticket.AdminUserFileResponse;
import com.analyzer.api.dto.legalticket.TicketSummaryResponse;
import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;

import java.util.List;

public interface AdminTicketManagementService {

    LegalTicketResponse assignLawyer(String ticketId, Long adminId, AssignLawyerRequest request);

    LegalTicketResponse reassignLawyer(String ticketId, Long adminId, AssignLawyerRequest request);

    LegalTicketResponse approveInternal(String ticketId, Long adminId);

    LegalTicketResponse closeInternal(String ticketId, Long adminId, String note);

    TicketSummaryResponse viewAiSummary(String ticketId);

    AdminChatHistoryResponse viewChatHistory(String ticketId);

    List<AdminUserFileResponse> viewUserFiles(String ticketId);
}
