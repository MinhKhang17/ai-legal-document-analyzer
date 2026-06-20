package com.analyzer.api.config;

import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.repository.RoleRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Automatically cleans legacy database columns and seeds default roles,
 * default users, and subscription plans on startup.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 1. Clean legacy columns from previous schemas (if they exist)
        try {
            logger.info("Cleaning up legacy database columns...");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS role CASCADE");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS refresh_token CASCADE");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS username CASCADE");
            jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_analyses CASCADE");
            jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_documents CASCADE");
            jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_chat_messages CASCADE");
            jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_expert_reviews CASCADE");
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

        // 3. Seed default users
        logger.info("Seeding default users...");
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new RuntimeException("ADMIN role not found in database"));
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found in database"));

        seedUser("admin", "admin", "admin@123", "pass@123", adminRole);
        seedUser("user", "user", "user@123", "pass@123", customerRole);

        // 4. Seed Subscription Plans
        logger.info("Checking for default Subscription Plans...");
        if (subscriptionPlanRepository.count() == 0) {
            SubscriptionPlan freePlan = SubscriptionPlan.builder()
                    .planName("Gói Miễn Phí")
                    .planType("FREE")
                    .description("Gói cơ bản trải nghiệm dịch vụ phân tích văn bản pháp lý")
                    .price(BigDecimal.ZERO)
                    .durationDays(30)
                    .maxQuota(5)
                    .active(true)
                    .build();

            SubscriptionPlan standardPlan = SubscriptionPlan.builder()
                    .planName("Gói Tiêu Chuẩn")
                    .planType("MONTHLY")
                    .description("Gói tiêu chuẩn cho cá nhân, truy cập nhiều lượt phân tích và chat")
                    .price(new BigDecimal("150000"))
                    .durationDays(30)
                    .maxQuota(50)
                    .active(true)
                    .build();

            SubscriptionPlan premiumPlan = SubscriptionPlan.builder()
                    .planName("Gói Cao Cấp")
                    .planType("MONTHLY")
                    .description("Gói cao cấp không giới hạn tính năng cho chuyên gia pháp lý")
                    .price(new BigDecimal("499000"))
                    .durationDays(30)
                    .maxQuota(200)
                    .active(true)
                    .build();

            subscriptionPlanRepository.saveAll(Arrays.asList(freePlan, standardPlan, premiumPlan));
            logger.info("Seeded 3 default Subscription Plans: Gói Miễn Phí, Gói Tiêu Chuẩn, Gói Cao Cấp.");
        } else {
            logger.info("Subscription Plans already exist.");
        }
    }

    private void seedUser(String firstName, String lastName, String email, String rawPassword, Role role) {
        User user = userRepository.findByEmail(email)
                .orElseGet(User::new);

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setAcceptedTerms(true);
        user.setRole(role);
        user.setActive(true);

        userRepository.save(user);
        logger.info("Seeded default user: {} / {}", email, rawPassword);
    }
}
