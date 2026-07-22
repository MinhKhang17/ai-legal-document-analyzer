package com.analyzer.api.service.notification.impl;

import com.analyzer.api.service.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender primaryMailSender;
    private final JavaMailSender backupMailSender;

    public EmailServiceImpl(@Qualifier("primaryMailSender") JavaMailSender primaryMailSender,
                             @Qualifier("backupMailSender") JavaMailSender backupMailSender) {
        this.primaryMailSender = primaryMailSender;
        this.backupMailSender = backupMailSender;
    }

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.primary.username:}")
    private String primaryUsername;

    @Value("${app.mail.primary.from:}")
    private String primaryFrom;

    @Value("${app.mail.backup.username:}")
    private String backupUsername;

    @Value("${app.mail.backup.from:}")
    private String backupFrom;

    @Value("${app.mail.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    // A blank "from" in config falls back to the account's username: an explicit
    // "from" key present-but-empty in a .env file (e.g. "MAIL_FROM=") is a value
    // Spring placeholder defaults won't fall back from, since the property is
    // technically present, so the fallback has to happen here instead of in YAML.
    private String effectiveFrom(String from, String username) {
        return StringUtils.hasText(from) ? from : username;
    }

    @Override
    @Async
    public void sendVerificationEmailAsync(String toEmail, String recipientName, String token) {
        sendVerificationEmail(toEmail, recipientName, token);
    }

    @Override
    public boolean sendVerificationEmail(String toEmail, String recipientName, String token) {
        String verifyUrl = frontendBaseUrl + "/verify-email?token=" + token;
        String body = "Xin chào " + safeName(recipientName) + ",\n\n"
                + "Vui lòng xác thực địa chỉ email của bạn bằng cách truy cập liên kết dưới đây (hết hạn sau 24 giờ):\n"
                + verifyUrl + "\n\n"
                + "Nếu bạn không thực hiện đăng ký này, vui lòng bỏ qua email này.";
        return send(toEmail, "Xác thực địa chỉ email của bạn", body);
    }

    @Override
    @Async
    public void sendIngestionSuccessEmailAsync(String toEmail, String recipientName, String originalFileName) {
        String body = "Xin chào " + safeName(recipientName) + ",\n\n"
                + "Tài liệu \"" + originalFileName + "\" của bạn đã được xử lý xong và sẵn sàng để phân tích/hỏi đáp.\n"
                + "Vui lòng quay lại workspace của bạn để tiếp tục.";
        send(toEmail, "Tài liệu của bạn đã sẵn sàng", body);
    }

    @Override
    @Async
    public void sendExpertAccountCreatedEmailAsync(String toEmail, String recipientName, String temporaryPassword, int passwordChangeDeadlineDays) {
        String loginUrl = frontendBaseUrl + "/login";
        String body = "Xin chào " + safeName(recipientName) + ",\n\n"
                + "Tài khoản Expert của bạn trên LexiGuard đã được tạo/kích hoạt với thông tin đăng nhập sau:\n"
                + "Email: " + toEmail + "\n"
                + "Mật khẩu tạm thời: " + temporaryPassword + "\n\n"
                + "Vui lòng đăng nhập tại " + loginUrl + " và đổi mật khẩu trong vòng " + passwordChangeDeadlineDays + " ngày kể từ khi nhận email này.\n"
                + "Nếu quá thời hạn trên mà mật khẩu chưa được đổi, tài khoản sẽ bị tạm khóa. Khi đó, vui lòng liên hệ Admin để được cấp lại quyền truy cập.";
        send(toEmail, "Thông tin tài khoản Expert của bạn", body);
    }

    @Override
    @Async
    public void sendTicketNotificationAsync(String toEmail, String recipientName, String ticketId,
                                            String ticketType, String ticketStatus, String relativePath,
                                            String detail) {
        String ticketUrl = frontendBaseUrl + (StringUtils.hasText(relativePath) ? relativePath : "");
        String body = "Xin chao " + safeName(recipientName) + ",\n\n"
                + "Ticket " + ticketId + " vua duoc cap nhat.\n"
                + "Loai: " + ticketType + "\n"
                + "Trang thai: " + ticketStatus + "\n"
                + (StringUtils.hasText(detail) ? detail + "\n" : "")
                + (StringUtils.hasText(relativePath) ? "Mo ticket: " + ticketUrl : "");
        send(toEmail, "Cap nhat ticket " + ticketId, body);
    }

    @Override
    @Async
    public void sendRefundConfirmationEmailAsync(String toEmail, String recipientName, Long refundId, String token) {
        String url = frontendBaseUrl + "/billing/refunds/confirm?token=" + token;
        String body = "Xin chao " + safeName(recipientName) + ",\n\n"
                + "Yeu cau hoan tien #" + refundId + " da duoc tao. Vui long xac nhan email trong 24 gio:\n"
                + url + "\n\nAdmin chi co the tao refund order sau khi ban xac nhan.";
        send(toEmail, "Xac nhan yeu cau hoan tien #" + refundId, body);
    }

    @Override
    @Async
    public void sendKnowledgeIngestedEmailAsync(String toEmail, String recipientName, String title,
                                                String code, String versionId, String jobId, String ingestedAt) {
        String body = "Xin chao " + safeName(recipientName) + ",\n\n"
                + "Tai lieu knowledge base da ingest thanh cong.\n"
                + "Title: " + title + "\nCode: " + code + "\nVersion ID: " + versionId
                + "\nJob ID: " + jobId + "\nIngested at: " + ingestedAt
                + "\nStatus: INGESTED\n\nVui long review va publish de AI co the su dung.";
        send(toEmail, "Knowledge base da ingest thanh cong: " + title, body);
    }

    @Override
    @Async
    public void sendPasswordResetEmailAsync(String toEmail, String recipientName, String token, int validMinutes) {
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        String body = "Xin chao " + safeName(recipientName) + ",\n\n"
                + "He thong nhan duoc yeu cau dat lai mat khau cua ban. Link co hieu luc trong "
                + validMinutes + " phut:\n" + resetUrl
                + "\n\nNeu ban khong yeu cau, vui long bo qua email nay.";
        send(toEmail, "Dat lai mat khau LexiGuard", body);
    }

    @Override
    public boolean sendFinancialEmail(String toEmail, String subject, String body) {
        return send(toEmail, subject, body);
    }

    private boolean send(String toEmail, String subject, String body) {
        String from = effectiveFrom(primaryFrom, primaryUsername);
        if (!mailEnabled || !StringUtils.hasText(from)) {
            logger.warn("Mail is not configured (app.mail.enabled/primary.username missing) - skipping email to {}", toEmail);
            return false;
        }

        if (trySend(primaryMailSender, from, toEmail, subject, body)) {
            return true;
        }

        if (!StringUtils.hasText(backupUsername)) {
            return false;
        }

        logger.warn("Primary SMTP failed, retrying with backup SMTP account for {}", toEmail);
        return trySend(backupMailSender, effectiveFrom(backupFrom, backupUsername), toEmail, subject, body);
    }

    private boolean trySend(JavaMailSender sender, String from, String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (MailException ex) {
            logger.error("Failed to send email to {} via {}: {}", toEmail, from, ex.getMessage());
            return false;
        }
    }

    private String safeName(String name) {
        return StringUtils.hasText(name) ? name : "bạn";
    }
}
