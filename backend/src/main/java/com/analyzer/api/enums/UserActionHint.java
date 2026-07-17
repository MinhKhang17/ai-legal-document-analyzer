package com.analyzer.api.enums;

/**
 * Simple UI hint so the frontend can decide whether to continue chatting,
 * request more facts, or show a ticket-creation action.
 */
public enum UserActionHint {
    CONTINUE_CHAT,
    PROVIDE_MORE_INFO,
    CREATE_TICKET,
    UPLOAD_CONTRACT,
    CONTACT_LAWYER
}
