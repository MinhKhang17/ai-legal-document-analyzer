package com.analyzer.api.config;

import com.analyzer.api.entity.Role;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Automatically cleans legacy database columns and seeds default roles on
 * startup.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 1. Clean legacy columns from previous schemas (if they exist)
        try {
            logger.info("Cleaning up legacy database columns...");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS role CASCADE");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS refresh_token CASCADE");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS username CASCADE");
            logger.info("Legacy columns cleaned up successfully.");
        } catch (Exception e) {
            logger.warn("Failed to clean up legacy columns: {}", e.getMessage());
        }

        // 2. Seed Roles
        logger.info("Seeding default roles...");
        Arrays.stream(RoleName.values()).forEach(roleName -> {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role role = Role.builder()
                        .name(roleName)
                        .build();
                roleRepository.save(role);
                logger.info("Seeded role: {}", roleName);
            }
        });
    }
}
