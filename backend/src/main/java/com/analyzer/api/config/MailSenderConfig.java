package com.analyzer.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class MailSenderConfig {

    private final MailAccountsProperties mailAccounts;

    @Bean
    public JavaMailSender primaryMailSender() {
        return buildSender(mailAccounts.getPrimary());
    }

    @Bean
    public JavaMailSender backupMailSender() {
        return buildSender(mailAccounts.getBackup());
    }

    private JavaMailSender buildSender(MailAccountsProperties.Account account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(account.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
