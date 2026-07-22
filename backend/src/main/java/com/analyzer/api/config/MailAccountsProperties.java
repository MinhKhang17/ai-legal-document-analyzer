package com.analyzer.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailAccountsProperties {

    private Account primary = new Account();
    private Account backup = new Account();

    @Getter
    @Setter
    public static class Account {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String from;
    }
}
