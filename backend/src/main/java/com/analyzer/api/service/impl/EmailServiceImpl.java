package com.analyzer.api.service.impl;

import com.analyzer.api.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

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
        if (!mailEnabled || !StringUtils.hasText(mailFrom)) {
            logger.warn("Mail is not configured (app.mail.enabled/from/SMTP_USERNAME missing) - skipping email to {}", toEmail);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (MailException ex) {
            logger.error("Failed to send email to {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    private String safeName(String name) {
        return StringUtils.hasText(name) ? name : "bạn";
    }
}
