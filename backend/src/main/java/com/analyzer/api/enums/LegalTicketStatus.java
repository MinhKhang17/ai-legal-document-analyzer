package com.analyzer.api.enums;

/**
 * Lifecycle state for a legal help ticket.
 */
public enum LegalTicketStatus {
    PENDING_ADMIN_REVIEW,
    ASSIGNED_TO_LAWYER,
    LAWYER_ANSWERED,
    CLOSED,
    REJECTED
}
