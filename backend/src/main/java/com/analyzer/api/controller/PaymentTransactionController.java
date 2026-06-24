package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponseDTO;
import com.analyzer.api.dto.paymenttransaction.VnPayIpnResponseDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-transactions")
@RequiredArgsConstructor
@Tag(
        name = "Quản lý giao dịch thanh toán",
        description = "Các API dùng để xem lịch sử giao dịch thanh toán và xử lý thanh toán qua VNPAY"
)
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy lịch sử giao dịch của tôi",
            description = "Khách hàng xem danh sách giao dịch thanh toán của chính mình."
    )
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponseDTO>>> getMyTransactions() {
        Long customerId = getCurrentUserId();
        List<PaymentTransactionResponseDTO> response = paymentTransactionService.getMyTransactions(customerId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy lịch sử thanh toán thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lấy toàn bộ lịch sử giao dịch",
            description = "Admin xem toàn bộ giao dịch thanh toán trong hệ thống."
    )
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponseDTO>>> getAllTransactions() {
        List<PaymentTransactionResponseDTO> response = paymentTransactionService.getAllTransactions();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy toàn bộ lịch sử thanh toán thành công", response));
    }

    @PostMapping("/{id}/vnpay-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Tạo URL thanh toán VNPAY",
            description = "Tạo đường dẫn thanh toán VNPAY cho một giao dịch đang chờ thanh toán."
    )
    public ResponseEntity<ApiResponseDTO<PaymentUrlResponseDTO>> createVnPayPaymentUrl(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long customerId = getCurrentUserId();
        PaymentUrlResponseDTO response = paymentTransactionService.createVnPayPaymentUrl(
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
    public ResponseEntity<ApiResponseDTO<PaymentTransactionResponseDTO>> handleVnPayReturn(
            @RequestParam Map<String, String> params) {
        PaymentTransactionResponseDTO response = paymentTransactionService.handleVnPayCallback(params);
        return ResponseEntity.ok(ApiResponseDTO.success("Xử lý kết quả thanh toán VNPAY thành công", response));
    }

    @GetMapping("/vnpay-ipn")
    @Operation(
            summary = "Xử lý IPN từ VNPAY",
            description = "Xác thực thông báo IPN từ VNPAY và cập nhật trạng thái giao dịch."
    )
    public ResponseEntity<VnPayIpnResponseDTO> handleVnPayIpn(@RequestParam Map<String, String> params) {
        paymentTransactionService.handleVnPayCallback(params);
        return ResponseEntity.ok(new VnPayIpnResponseDTO("00", "Confirm Success"));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }

        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
