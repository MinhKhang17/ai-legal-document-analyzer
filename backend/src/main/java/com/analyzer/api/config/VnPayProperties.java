package com.analyzer.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnPayProperties {

    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String returnUrl = "http://localhost:8080/api/v1/payment-transactions/vnpay-return";
    private String tmnCode = "CHANGE_ME";
    private String hashSecret = "CHANGE_ME";
    private String version = "2.1.0";
    private String command = "pay";
    private String currCode = "VND";
    private String orderType = "other";
    private String locale = "vn";
}
