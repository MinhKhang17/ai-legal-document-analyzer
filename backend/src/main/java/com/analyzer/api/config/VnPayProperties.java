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

    private String payUrl;
    private String returnUrl;
    private String tmnCode;
    private String hashSecret;
    private String version;
    private String command;
    private String currCode;
    private String orderType;
    private String locale;
}