package com.analyzer.api.e2e;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.SubscriptionPlan;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import com.analyzer.api.repository.chatsession.ChatSessionRepository;
import com.analyzer.api.repository.customerplan.CustomerPlanRepository;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.user.RoleRepository;
import com.analyzer.api.repository.subscriptionplan.SubscriptionPlanRepository;
import com.analyzer.api.repository.paymenttransaction.PaymentTransactionRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.service.customerplan.CustomerPlanService;
import com.analyzer.api.service.paymenttransaction.PaymentTransactionService;
import com.analyzer.api.service.subscriptionplan.SubscriptionPlanService;
import com.analyzer.api.service.subscription.SubscriptionQuotaService;
import com.analyzer.api.service.workspace.WorkspaceService;
import com.analyzer.api.service.customerplan.impl.CustomerPlanSnapshotHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

// Shared fixtures for the muc 18 E2E scenarios (Plan_Flow_Test_Cases.md), running against a
// real, ephemeral Postgres: the mechanisms under test (pg_advisory_xact_lock, the partial unique
// indexes enforcing "at most one ACTIVE customer_plan" / "unique plan name", VNPay HMAC
// verification) cannot be exercised faithfully with mocks or H2. Also builds the muc 6 test-data
// boundary states (user/plan states, endDate +/-, byte-size boundaries) as reusable helpers.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractPlanFlowE2ETest {

    // A single container shared across every subclass/test method — Spring's test
    // context cache
    // reuses one ApplicationContext for all of them since configuration is
    // identical, so this is
    // started once for the whole E2E suite, not once per test class.
    // @ServiceConnection wires
    // spring.datasource.* to this container automatically (Flyway/Hibernate then
    // run against it).
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void vnpayTestProperties(DynamicPropertyRegistry registry) {
        // A known, fixed HMAC secret so tests can sign fake-but-valid VNPay callback
        // params
        // themselves instead of depending on VNPay's real sandbox being reachable.
        registry.add("app.payment.vnpay.hash-secret", () -> "e2e-test-hash-secret");
        registry.add("app.payment.vnpay.tmn-code", () -> "E2ETEST");
        // No SMTP_USERNAME/app.mail.from in test config: EmailServiceImpl already
        // no-ops
        // (logs + skips) whenever mailFrom is blank, so no additional mocking is needed
        // here.
    }

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    protected WorkspaceRepository workspaceRepository;
    @Autowired
    protected DocumentRepository documentRepository;
    @Autowired
    protected ChatSessionRepository chatSessionRepository;
    @Autowired
    protected ChatMessageRepository chatMessageRepository;
    @Autowired
    protected CustomerPlanRepository customerPlanRepository;
    @Autowired
    protected CustomerPlanSnapshotHelper customerPlanSnapshotHelper;
    @Autowired
    protected PaymentTransactionRepository paymentTransactionRepository;
    @Autowired
    protected CustomerPlanService customerPlanService;
    @Autowired
    protected SubscriptionQuotaService subscriptionQuotaService;
    @Autowired
    protected PaymentTransactionService paymentTransactionService;
    @Autowired
    protected WorkspaceService workspaceService;
    @Autowired
    protected SubscriptionPlanService subscriptionPlanService;

    private static final String VNPAY_HASH_SECRET = "e2e-test-hash-secret";

    protected User createCustomer(String emailLocalPart) {
        Role role = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not seeded"));
        User user = User.builder()
                .firstName("E2E")
                .lastName("Customer")
                .email(emailLocalPart + "+" + UUID.randomUUID() + "@e2e.test")
                .password("not-used-in-these-tests")
                .acceptedTerms(true)
                .role(role)
                .active(true)
                .emailVerified(true)
                .build();
        return userRepository.save(user);
    }

    protected SubscriptionPlan planByType(String planType) {
        return subscriptionPlanRepository.findByPlanTypeIgnoreCase(planType)
                .orElseThrow(() -> new IllegalStateException(planType + " plan not seeded"));
    }

    protected Workspace createWorkspace(User owner) {
        LocalDateTime now = LocalDateTime.now();
        Workspace workspace = Workspace.builder()
                .id("ws_" + UUID.randomUUID().toString().replace("-", ""))
                .user(owner)
                .name("E2E workspace")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return workspaceRepository.save(workspace);
    }

    // Inserts an already-successfully-analyzed document directly (bypassing the
    // real
    // upload -> external AI-service HTTP call, which isn't available in this test
    // environment)
    // so analysis-quota tests (PLAN-QD-03 / muc 18) can control processedAt
    // precisely without
    // depending on a running Python AI service.
    protected Document createReadyDocument(Workspace workspace, User owner, LocalDateTime processedAt,
            long fileSizeBytes) {
        LocalDateTime uploadedAt = processedAt.minusMinutes(1);
        Document document = Document.builder()
                .id("doc_" + UUID.randomUUID().toString().replace("-", ""))
                .workspace(workspace)
                .user(owner)
                .originalFileName("contract.pdf")
                .storedFileName(UUID.randomUUID() + "_contract.pdf")
                .filePath("/tmp/" + UUID.randomUUID())
                .fileType("application/pdf")
                .fileSize(fileSizeBytes)
                .sourceType("USER_DOCUMENT")
                .contractTypeConfirmed(false)
                .status("READY")
                .chunkCount(3)
                .uploadedAt(uploadedAt)
                .processedAt(processedAt)
                .updatedAt(processedAt)
                .build();
        return documentRepository.save(document);
    }

    // Directly persists a CustomerPlan in an arbitrary state (mục 6: ACTIVE with a
    // scheduled
    // downgrade, past/future endDate, etc.) without going through subscribe()/VNPay
    // — used to set
    // up the *pre-existing* state a scenario starts from, while the operation
    // actually under
    // test still goes through the real service layer.
    protected CustomerPlan persistCustomerPlan(User customer, SubscriptionPlan plan, PlanStatus status,
            LocalDateTime startDate, LocalDateTime endDate) {
        return persistCustomerPlan(customer, plan, status, startDate, endDate, null, null);
    }

    protected CustomerPlan persistCustomerPlan(User customer, SubscriptionPlan plan, PlanStatus status,
            LocalDateTime startDate, LocalDateTime endDate,
            SubscriptionPlan scheduledPlan, LocalDateTime planChangeEffectiveAt) {
        CustomerPlan customerPlan = CustomerPlan.builder()
                .customer(customer)
                .subscriptionPlan(plan)
                .status(status)
                .usedQuota(0)
                .startDate(startDate)
                .endDate(endDate)
                .usageStartAt(startDate)
                .usageEndAt(endDate)
                .billingCycleStartAt(startDate)
                .billingCycleEndAt(endDate)
                .scheduledSubscriptionPlan(scheduledPlan)
                .planChangeEffectiveAt(planChangeEffectiveAt)
                .autoRenew(false)
                .build();
        customerPlanSnapshotHelper.applySnapshot(customerPlan, plan);
        return customerPlanRepository.save(customerPlan);
    }

    // Inserts a completed assistant ChatMessage carrying totalTokens, the row
    // SubscriptionQuotaServiceImpl.getCurrentUsage() actually sums for AI-token
    // quota — created
    // "now" so it naturally falls inside a billing cycle that started at/around
    // "now" in the
    // same test, without needing to fight the entity's @PrePersist-managed
    // createdAt.
    protected void recordCompletedAssistantTokens(User owner, Workspace workspace, int totalTokens) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = ChatSession.builder()
                .id("cs_" + UUID.randomUUID().toString().replace("-", ""))
                .user(owner)
                .workspace(workspace)
                .title("E2E session")
                .status(ChatSessionStatus.ACTIVE)
                .build();
        session = chatSessionRepository.save(session);

        ChatMessage message = ChatMessage.builder()
                .id("msg_" + UUID.randomUUID().toString().replace("-", ""))
                .chatSession(session)
                .user(owner)
                .role(ChatMessageRole.ASSISTANT)
                .messageType(ChatMessageType.NORMAL_CHAT)
                .content("E2E assistant reply")
                .status(ChatMessageStatus.COMPLETED)
                .totalTokens(totalTokens)
                .build();
        chatMessageRepository.save(message);
    }

    // Mirrors PaymentTransactionServiceImpl's private
    // buildHashData/hmacSha512/toVnPayAmount so
    // tests can produce a callback payload that passes real HMAC verification, the
    // same way
    // VNPay's sandbox would sign it — without depending on VNPay being reachable in
    // CI.
    protected Map<String, String> signedVnPayCallback(String transactionCode, BigDecimal amount,
            String responseCode, String transactionStatus) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", transactionCode);
        params.put("vnp_Amount", toVnPayAmount(amount));
        params.put("vnp_TransactionNo", "VNP" + UUID.randomUUID().toString().substring(0, 8));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_BankCode", "NCB");

        Map<String, String> signed = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, TreeMap::new));
        String secureHash = hmacSha512(VNPAY_HASH_SECRET, buildHashData(signed));

        Map<String, String> result = new LinkedHashMap<>(params);
        result.put("vnp_SecureHash", secureHash);
        return result;
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSha512(String secretKey, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            hmac512.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
