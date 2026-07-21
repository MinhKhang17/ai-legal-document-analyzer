package com.analyzer.api.config;

import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.enums.SubscriptionTier;
import com.analyzer.api.repository.RoleRepository;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.repository.SubscriptionPlanRepository;
import com.analyzer.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

        private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

        private final RoleRepository roleRepository;
        private final UserRepository userRepository;
        private final SubscriptionPlanRepository subscriptionPlanRepository;
        private final CustomerPlanRepository customerPlanRepository;
        private final PaymentTransactionRepository paymentTransactionRepository;
        private final PasswordEncoder passwordEncoder;
        private final JdbcTemplate jdbcTemplate;

        @Value("${app.admin.email:admin@123}")
        private String adminEmail;

        @Override
        public void run(String... args) {
                try {
                        logger.info("Cleaning up legacy database columns...");
                        jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS role CASCADE");
                        jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS refresh_token CASCADE");
                        jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS username CASCADE");
                        jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_analyses CASCADE");
                        jdbcTemplate.execute("ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_documents CASCADE");
                        jdbcTemplate.execute(
                                        "ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_chat_messages CASCADE");
                        jdbcTemplate.execute(
                                        "ALTER TABLE customer_plans DROP COLUMN IF EXISTS used_expert_reviews CASCADE");
                        jdbcTemplate.execute("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS share_access_level VARCHAR(255) DEFAULT 'RESTRICTED'");
                        jdbcTemplate.execute("UPDATE chat_sessions SET share_access_level = 'RESTRICTED' WHERE share_access_level IS NULL");
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

                seedUser("admin", "admin", adminEmail, "pass@123" +
                                "", adminRole);
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

                grantPremiumPlanToDemoUser();
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
                user.setEmailVerified(true);
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
                if ("FREE".equals(planType)) {
                        plan.setStorageLimitMb(50);
                        plan.setMaxFileSizeMb(10);
                        plan.setMaxAttachedDocumentsPerSession(1);
                        plan.setAllowSystemErrorTicket(true);
                        plan.setAllowQueryErrorTicket(true);
                        plan.setAllowContactExpertTicket(false);
                        plan.setFeatureLimitsJson("[\"5 analyses\",\"50,000 AI tokens\",\"1 workspace\",\"System and query error support\"]");
                } else if ("STANDARD".equals(planType)) {
                        plan.setStorageLimitMb(1024);
                        plan.setMaxFileSizeMb(25);
                        plan.setMaxAttachedDocumentsPerSession(5);
                        plan.setAllowSystemErrorTicket(true);
                        plan.setAllowQueryErrorTicket(true);
                        plan.setAllowContactExpertTicket(false);
                        plan.setFeatureLimitsJson("[\"50 analyses\",\"1,500,000 AI tokens\",\"5 workspaces\",\"System and query error support\"]");
                } else {
                        plan.setStorageLimitMb(5120);
                        plan.setMaxFileSizeMb(50);
                        plan.setMaxAttachedDocumentsPerSession(15);
                        plan.setAllowSystemErrorTicket(true);
                        plan.setAllowQueryErrorTicket(true);
                        plan.setAllowContactExpertTicket(true);
                        plan.setFeatureLimitsJson("[\"200 analyses\",\"8,500,000 AI tokens\",\"20 workspaces\",\"1 expert ticket per month\"]");
                }
                plan.setActive(true);

                subscriptionPlanRepository.save(plan);
                logger.info("Seeded or updated subscription plan {}", planType);
        }

        private void grantPremiumPlanToDemoUser() {
                User demoUser = userRepository.findByEmail("user@123")
                                .orElseThrow(() -> new IllegalStateException("Demo customer user@123 was not seeded"));
                SubscriptionPlan premiumPlan = subscriptionPlanRepository.findByPlanTypeIgnoreCase("PREMIUM")
                                .orElseThrow(() -> new IllegalStateException("PREMIUM plan was not seeded"));

                LocalDateTime now = LocalDateTime.now();
                List<CustomerPlan> demoPlans = customerPlanRepository.findByCustomerId(demoUser.getId());
                CustomerPlan demoPremiumPlan = demoPlans.stream()
                                .filter(plan -> plan.getSubscriptionPlan() != null
                                                && premiumPlan.getId().equals(plan.getSubscriptionPlan().getId()))
                                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE
                                                && (plan.getEndDate() == null || plan.getEndDate().isAfter(now)))
                                .findFirst()
                                .orElse(null);

                for (CustomerPlan plan : demoPlans) {
                        if (plan.getStatus() == PlanStatus.ACTIVE
                                        && (demoPremiumPlan == null || !plan.getId().equals(demoPremiumPlan.getId()))) {
                                plan.setStatus(PlanStatus.EXPIRED);
                                customerPlanRepository.save(plan);
                        }
                }

                if (demoPremiumPlan == null) {
                        demoPremiumPlan = demoPlans.stream()
                                        .filter(plan -> plan.getSubscriptionPlan() != null
                                                        && premiumPlan.getId()
                                                                        .equals(plan.getSubscriptionPlan().getId()))
                                        .findFirst()
                                        .orElseGet(() -> CustomerPlan.builder()
                                                        .customer(demoUser)
                                                        .subscriptionPlan(premiumPlan)
                                                        .build());

                        demoPremiumPlan.setStatus(PlanStatus.ACTIVE);
                        demoPremiumPlan.setStartDate(now);
                        demoPremiumPlan.setEndDate(now.plusDays(premiumPlan.getDurationDays()));
                        demoPremiumPlan.setUsageStartAt(now);
                        demoPremiumPlan.setUsageEndAt(now.plusDays(premiumPlan.getDurationDays()));
                        demoPremiumPlan.setBillingCycleStartAt(now);
                        demoPremiumPlan.setBillingCycleEndAt(now.plusDays(premiumPlan.getDurationDays()));
                        demoPremiumPlan.setUsedQuota(0);
                        demoPremiumPlan.setAutoRenew(false);
                        demoPremiumPlan.setCancelReason(null);
                        demoPremiumPlan = customerPlanRepository.save(demoPremiumPlan);
                }

                cancelPendingDemoPayments(demoPremiumPlan);
                logger.info(
                                "Granted active {} plan to demo customer {} until {}",
                                premiumPlan.getPlanName(),
                                demoUser.getEmail(),
                                demoPremiumPlan.getEndDate());
        }

        private void cancelPendingDemoPayments(CustomerPlan demoPremiumPlan) {
                for (PaymentTransaction transaction : paymentTransactionRepository
                                .findByCustomerPlanId(demoPremiumPlan.getId())) {
                        if (transaction.getPaymentStatus() == PaymentStatus.PENDING) {
                                transaction.setPaymentStatus(PaymentStatus.CANCELLED);
                                transaction.setGatewayResponseCode("DEMO_PLAN_GRANTED");
                                paymentTransactionRepository.save(transaction);
                        }
                }
        }
}
