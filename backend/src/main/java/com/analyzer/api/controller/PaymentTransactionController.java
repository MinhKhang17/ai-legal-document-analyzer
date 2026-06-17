package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponseDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-transactions")
@RequiredArgsConstructor
@Tag(name = "Payment Transaction Management", description = "APIs for payment transaction history and simulation")
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my transaction history", description = "Customer views their own payment transactions.")
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponseDTO>>> getMyTransactions() {
        Long customerId = getCurrentUserId();
        List<PaymentTransactionResponseDTO> response = paymentTransactionService.getMyTransactions(customerId);
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy lịch sử thanh toán thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all transaction histories", description = "Admin views all payment transactions in the system.")
    public ResponseEntity<ApiResponseDTO<List<PaymentTransactionResponseDTO>>> getAllTransactions() {
        List<PaymentTransactionResponseDTO> response = paymentTransactionService.getAllTransactions();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy toàn bộ lịch sử thanh toán thành công", response));
    }

    @PutMapping("/{id}/success")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Simulate payment success", description = "Simulates a successful payment. Activates the customer plan.")
    public ResponseEntity<ApiResponseDTO<PaymentTransactionResponseDTO>> simulateSuccess(@PathVariable Long id) {
        PaymentTransactionResponseDTO response = paymentTransactionService.simulateSuccess(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Giả lập thanh toán thành công", response));
    }

    @PutMapping("/{id}/failed")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Simulate payment failure", description = "Simulates a failed payment. Plan remains pending or cancelled.")
    public ResponseEntity<ApiResponseDTO<PaymentTransactionResponseDTO>> simulateFailed(@PathVariable Long id) {
        PaymentTransactionResponseDTO response = paymentTransactionService.simulateFailed(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Giả lập thanh toán thất bại", response));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
