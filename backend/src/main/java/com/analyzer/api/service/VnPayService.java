package com.analyzer.api.service;

import com.analyzer.api.config.VnPayProperties;
import com.analyzer.api.entity.PaymentTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VnPayService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties properties;

    public String createPaymentUrl(PaymentTransaction transaction, String clientIp) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", toVnPayAmount(transaction.getAmount()));
        params.put("vnp_CurrCode", properties.getCurrCode());
        params.put("vnp_TxnRef", transaction.getTransactionCode());
        params.put("vnp_OrderInfo", "Thanh toan goi dich vu " + transaction.getSubscriptionPlan().getPlanName());
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", LocalDateTime.now().format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15).format(VNPAY_DATE_FORMAT));

        String secureHash = hmacSha512(properties.getHashSecret(), buildHashData(params));
        return UriComponentsBuilder.fromUriString(properties.getPayUrl())
                .query(buildQuery(params))
                .queryParam("vnp_SecureHash", secureHash)
                .build(true)
                .toUriString();
    }

    public boolean isValidSignature(Map<String, String> callbackParams) {
        String receivedHash = callbackParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        Map<String, String> signedParams = callbackParams.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, TreeMap::new));

        String calculatedHash = hmacSha512(properties.getHashSecret(), buildHashData(signedParams));
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String secretKey, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Khong the tao chu ky thanh toan VNPAY", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
