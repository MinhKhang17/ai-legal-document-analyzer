package com.analyzer.api.service.admin;

import com.analyzer.api.dto.legalticket.AssignLawyerRequest;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;

/**
 * Service interface for admin actions related to ticket assignment.
 */
public interface AdminTicketAssignmentService {

    LegalTicketResponse assignLawyer(Long adminId, String ticketId, AssignLawyerRequest request);

    LegalTicketResponse reassignLawyer(Long adminId, String ticketId, AssignLawyerRequest request);
}
