package com.analyzer.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LegalAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalAnalyzerApplication.class, args);
    }
}
