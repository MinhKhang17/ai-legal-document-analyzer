package com.analyzer.api.mapper;

import com.analyzer.api.dto.legalticket.LegalTicketMessageResponse;
import com.analyzer.api.dto.legalticket.LegalTicketResponse;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.LegalTicketMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LegalTicketMapper {

    @Mapping(target = "workspaceId", source = "workspace.id")
    @Mapping(target = "workspaceName", source = "workspace.name")
    @Mapping(target = "documentId", source = "document.id")
    @Mapping(target = "documentName", source = "document.originalFileName")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getFirstName() + \" \" + ticket.getCreatedBy().getLastName() : null)")
    @Mapping(target = "assignedLawyerId", source = "assignedLawyer.id")
    @Mapping(target = "assignedLawyerName", expression = "java(ticket.getAssignedLawyer() != null ? ticket.getAssignedLawyer().getFirstName() + \" \" + ticket.getAssignedLawyer().getLastName() : null)")
    @Mapping(target = "sharedDocumentIds", ignore = true)
    @Mapping(target = "contextSnapshot", ignore = true)
    LegalTicketResponse toResponse(LegalTicket ticket);

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", expression = "java(message.getSender() != null ? message.getSender().getFirstName() + \" \" + message.getSender().getLastName() : null)")
    @Mapping(target = "senderRole", expression = "java(message.getSenderRole() != null ? message.getSenderRole() : message.getSender() != null && message.getSender().getRole() != null && message.getSender().getRole().getName() != null ? message.getSender().getRole().getName().name() : null)")
    @Mapping(target = "replyToMessageId", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    LegalTicketMessageResponse toMessageResponse(LegalTicketMessage message);
}
