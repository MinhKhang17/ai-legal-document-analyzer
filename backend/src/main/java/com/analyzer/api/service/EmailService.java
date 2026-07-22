package com.analyzer.api.service;

public interface EmailService {

    void sendVerificationEmailAsync(String toEmail, String recipientName, String token);

    boolean sendVerificationEmail(String toEmail, String recipientName, String token);

    void sendIngestionSuccessEmailAsync(String toEmail, String recipientName, String originalFileName);

    void sendExpertAccountCreatedEmailAsync(String toEmail, String recipientName, String temporaryPassword,
            int passwordChangeDeadlineDays);

    void sendTicketNotificationAsync(String toEmail, String recipientName, String ticketId,
            String ticketType, String ticketStatus, String relativePath,
            String detail);

    void sendRefundConfirmationEmailAsync(String toEmail, String recipientName, Long refundId, String token);

    void sendKnowledgeIngestedEmailAsync(String toEmail, String recipientName, String title,
            String code, String versionId, String jobId, String ingestedAt);

    void sendPasswordResetEmailAsync(String toEmail, String recipientName, String token, int validMinutes);

    boolean sendFinancialEmail(String toEmail, String subject, String body);
}
