package com.analyzer.api.enums;

/**
 * Lifecycle state for a legal help ticket.
 */
public enum LegalTicketStatus {
    OPEN,
    ASSIGNED,
    WAITING_FOR_USER,
    WAITING_FOR_EXPERT,
    DRAFT,
    PENDING_ADMIN_REVIEW,
    REJECTED_BY_ADMIN,
    ASSIGNED_TO_LAWYER,
    IN_REVIEW,
    NEED_MORE_INFO,
    CUSTOMER_RESPONDED,
    RESOLVED,
    CLOSED,
    CANCELLED,
    REOPENED
}
