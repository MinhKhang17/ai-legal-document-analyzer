package com.analyzer.api.controller.paymenttransaction;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponse;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponse;
import com.analyzer.api.dto.paymenttransaction.VnPayIpnResponse;
import com.analyzer.api.exception.payment.VnPayCallbackException;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Quản lý giao dịch thanh toán",
        description = "Các API dùng để xem lịch sử giao dịch thanh toán và xử lý thanh toán qua VNPAY"
)
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @Value("${app.frontend.payment-result-url:http://localhost:5173/billing/payment-result}")
    private String paymentResultUrl;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy lịch sử giao dịch của tôi",
            description = "Khách hàng xem danh sách giao dịch thanh toán của chính mình."
    )
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponse>>> getMyTransactions(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Long customerId = currentUser.getId();
        List<PaymentTransactionResponse> response = paymentTransactionService.getMyTransactions(customerId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy lịch sử thanh toán thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lấy toàn bộ lịch sử giao dịch",
            description = "Admin xem toàn bộ giao dịch thanh toán trong hệ thống."
    )
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponse>>> getAllTransactions() {
        List<PaymentTransactionResponse> response = paymentTransactionService.getAllTransactions();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy toàn bộ lịch sử thanh toán thành công", response));
    }

    @PostMapping("/{id}/vnpay-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Tạo URL thanh toán VNPAY",
            description = "Tạo đường dẫn thanh toán VNPAY cho một giao dịch đang chờ thanh toán."
    )
    public ResponseEntity<ApiResponseDTO<PaymentUrlResponse>> createVnPayPaymentUrl(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable Long id,
            HttpServletRequest request) {
        Long customerId = currentUser.getId();
        PaymentUrlResponse response = paymentTransactionService.createVnPayPaymentUrl(
                id,
                customerId,
                getClientIp(request)
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Tạo URL thanh toán VNPAY thành công", response));
    }

    @GetMapping("/vnpay-return")
    @Operation(
            summary = "Xử lý kết quả trả về từ VNPAY",
            description = "Xác thực các tham số VNPAY trả về và cập nhật trạng thái giao dịch."
    )
    public ResponseEntity<Void> handleVnPayReturn(
            @RequestParam Map<String, String> params) {
        try {
            paymentTransactionService.handleVnPayCallback(params);
        } catch (Exception ex) {
            // Never let a callback validation failure surface as a raw JSON error page to the
            // browser — log it server-side and still redirect; the frontend reads vnp_ResponseCode
            // (already present in params) to render success/failure.
            log.warn("VNPay return callback failed: {}", ex.getMessage());
        }
        URI redirectUri = buildPaymentResultRedirectUri(params);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }

    @GetMapping("/vnpay-ipn")
    @Operation(
            summary = "Xử lý IPN từ VNPAY",
            description = "Xác thực thông báo IPN từ VNPAY và cập nhật trạng thái giao dịch."
    )
    public ResponseEntity<VnPayIpnResponse> handleVnPayIpn(@RequestParam Map<String, String> params) {
        try {
            paymentTransactionService.handleVnPayCallback(params);
            return ResponseEntity.ok(new VnPayIpnResponse("00", "Confirm Success"));
        } catch (VnPayCallbackException ex) {
            log.warn("VNPay IPN rejected: rspCode={} message={}", ex.getRspCode(), ex.getMessage());
            return ResponseEntity.ok(new VnPayIpnResponse(ex.getRspCode(), ex.getMessage()));
        } catch (Exception ex) {
            log.warn("VNPay IPN callback failed unexpectedly", ex);
            return ResponseEntity.ok(new VnPayIpnResponse("99", "Unknown error"));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private URI buildPaymentResultRedirectUri(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(paymentResultUrl);
        params.forEach(builder::queryParam);
        return builder.build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }
}
