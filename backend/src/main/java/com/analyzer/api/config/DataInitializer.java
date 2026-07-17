package com.analyzer.api.config;

import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.enums.SubscriptionTier;
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
    public void run(String... args) {
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
        } catch (Exception ex) {
            logger.warn("Failed to clean up legacy columns: {}", ex.getMessage());
        }

        logger.info("Seeding default roles...");
        Arrays.stream(RoleName.values()).forEach(roleName -> {
            if (roleRepository.findByName(roleName).isEmpty()) {
                roleRepository.save(Role.builder().name(roleName).build());
                logger.info("Seeded role: {}", roleName);
            }
        });

        logger.info("Seeding default users...");
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new RuntimeException("ADMIN role not found in database"));
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found in database"));
        Role expertRole = roleRepository.findByName(RoleName.EXPERT)
                .orElseThrow(() -> new RuntimeException("EXPERT role not found in database"));

        seedUser("admin", "admin", "admin@123", "pass@123", adminRole);
        seedUser("user", "user", "user@123", "pass@123", customerRole);
        seedUser("expert", "expert", "expert@123", "pass@123", expertRole);

        logger.info("Seeding subscription plans...");
        seedOrUpdatePlan("FREE", "Free Plan", SubscriptionTier.BASIC,
                "Free plan for trial legal analysis usage.",
                BigDecimal.ZERO, 30, 5, 50_000, 0, 1, 3, 1);
        seedOrUpdatePlan("STANDARD", "Standard Plan", SubscriptionTier.PRO,
                "Standard monthly plan for regular contract analysis and AI chat.",
                new BigDecimal("79000"), 30, 50, 1_500_000, 0, 5, 15, 10);
        seedOrUpdatePlan("PREMIUM", "Premium Plan", SubscriptionTier.PREMIUM,
                "Premium monthly plan with higher AI limits and one expert review ticket.",
                new BigDecimal("299000"), 30, 200, 8_500_000, 1, 20, 50, 40);
    }

    private void seedUser(String firstName, String lastName, String email, String rawPassword, Role role) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
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

    private void seedOrUpdatePlan(
            String planType,
            String planName,
            SubscriptionTier tier,
            String description,
            BigDecimal price,
            int durationDays,
            int maxQuota,
            int aiQuota,
            int ticketQuota,
            int maxWorkspaces,
            int maxContractsPerWorkspace,
            int maxDraftContracts) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByPlanTypeIgnoreCase(planType)
                .orElseGet(SubscriptionPlan::new);

        plan.setPlanType(planType);
        plan.setPlanName(planName);
        plan.setTier(tier);
        plan.setDescription(description);
        plan.setPrice(price);
        plan.setDurationDays(durationDays);
        plan.setMaxQuota(maxQuota);
        plan.setAiQuota(aiQuota);
        plan.setTicketQuota(ticketQuota);
        plan.setMaxWorkspaces(maxWorkspaces);
        plan.setMaxContractsPerWorkspace(maxContractsPerWorkspace);
        plan.setMaxDraftContracts(maxDraftContracts);
        plan.setFeatureLimitsJson(null);
        plan.setActive(true);

        subscriptionPlanRepository.save(plan);
        logger.info("Seeded or updated subscription plan {}", planType);
    }
}
