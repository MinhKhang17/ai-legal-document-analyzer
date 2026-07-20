# Đề Xuất Thiết Kế Các Gói Dịch Vụ Subscription Cho Ứng Dụng Phân Tích Hợp Đồng Pháp Lý (AI Legal Document Analyzer)

Tài liệu này phân tích chi tiết chi phí sử dụng mô hình AI hiện tại (dòng **Gemini Flash** - cụ thể là Gemini 2.0 Flash / 1.5 Flash) để đưa ra thiết kế phù hợp cho **các gói dịch vụ** (Subscription Plans) tối ưu hóa doanh thu và trải nghiệm người dùng.

---

## 1. Phân Tích Chi Phí Sử Dụng Mô Hình AI (Gemini 2.0 Flash)

Hiện tại hệ thống đang được cấu hình sử dụng mô hình **Gemini 2.0 Flash** (hoặc fallback là **Gemini 1.5 Flash**). Đây là dòng mô hình thế hệ mới của Google với ưu điểm vượt trội: tốc độ cực nhanh, context window lớn (1 triệu tokens) và đặc biệt là **chi phí API cực kỳ rẻ**.

### Bảng Giá API Gemini (Google AI Studio / Vertex AI)
*   **Gemini 1.5 Flash:**
    *   **Input (Đầu vào):** $0.075 / 1 triệu tokens (khoảng ~1.87 VNĐ)
    *   **Output (Đầu ra):** $0.300 / 1 triệu tokens (khoảng ~7.50 VNĐ)
*   **Gemini 2.0 Flash:**
    *   **Input (Đầu vào):** $0.100 / 1 triệu tokens (khoảng ~2.50 VNĐ)
    *   **Output (Đầu ra):** $0.400 / 1 triệu tokens (khoảng ~10.00 VNĐ)

*(Quy đổi tỷ giá tạm tính: 1 USD = 25,000 VNĐ)*

---

### Ước Tính Chi Phí AI Cho Từng Tính Năng

Giả định một hợp đồng pháp lý tiếng Việt trung bình có độ dài khoảng **10 trang** (~4,000 từ ≈ **8,000 tokens** do tiếng Việt ghép âm tiết làm số token tăng khoảng 1.5 - 2 lần so với tiếng Anh).

#### A. Tính năng: Upload & Phân tích hợp đồng (Upload & Analyze Contract)
*   **Thực tế triển khai trong mã nguồn (`pipeline.py`):** Hệ thống không gửi toàn bộ hợp đồng lớn lên AI một lúc. Thay vào đó, tệp hợp đồng được đọc cục bộ (local) bằng thư viện PDF/Docx Loader hoặc OCR chạy local (0 VNĐ). 
*   Văn bản được tách ra thành các **Điều khoản (Clauses)**. Với các điều khoản phức tạp có độ khớp thấp với bộ quy tắc sẵn có, hệ thống sẽ gửi truy vấn nhỏ lên Gemini để phân tích rủi ro độc lập:
    *   **Input mỗi điều khoản:** Đoạn văn điều khoản (giới hạn 420 ký tự ≈ 200 tokens) + 2 rủi ro ứng viên (≈ 100 tokens) = **300 tokens**.
    *   **Output mỗi điều khoản:** Kết quả JSON phân tích rủi ro ngắn (≈ **80 tokens**).
*   **Chi phí AI tương đương cho 1 điều khoản cần kiểm duyệt (Gemini 2.0 Flash):**
    *   *Input:* $300 \times (0.10 / 1M) \approx 0.00003 USD$ (~0.75 VNĐ)
    *   *Output:* $80 \times (0.40 / 1M) \approx 0.000032 USD$ (~0.80 VNĐ)
    *   **Cộng lại:** **~1.55 VNĐ / một điều khoản**.
*   **Tổng chi phí ước tính trên 1 hợp đồng (10 trang, trung bình có 15 điều khoản cần AI xác minh):**
    *   **Tổng cộng:** $15 \times 1.55 \text{ VNĐ} = $ **~23.25 VNĐ / lượt upload & phân tích**.
    *   *(So với ước tính ban đầu gộp cả file 10 trang gửi đi hết ~40 VNĐ, việc tách nhỏ theo mã nguồn giúp tiết kiệm chi phí và chạy ổn định hơn).*

#### B. Tính năng: Chat với AI (RAG Chat with Contract)
*   **Input:** Câu hỏi người dùng (200 tokens) + Lịch sử chat (800 tokens) + Văn cảnh lấy ra từ Database/Vector (2,000 tokens) = **3,000 tokens**.
*   **Output (AI trả lời):** Câu trả lời tư vấn pháp lý ≈ **400 tokens**.
*   **Chi phí AI tương đương (Gemini 2.0 Flash):**
    *   *Input:* $3,000 \times (0.10 / 1M) = 0.0003 USD$
    *   *Output:* $400 \times (0.40 / 1M) = 0.00016 USD$
    *   **Tổng cộng:** **0.00046 USD (~11.5 VNĐ) / một tin nhắn**.

#### C. Tính năng: Tạo/Soạn thảo hợp đồng (Create/Draft Contract)
*   **Input:** Prompt yêu cầu chi tiết + Template cấu trúc hợp đồng mẫu = **1,500 tokens**.
*   **Output:** Dự thảo hợp đồng pháp lý đầy đủ điều khoản (dài) ≈ **6,000 tokens**.
*   **Chi phí AI tương đương (Gemini 2.0 Flash):**
    *   *Input:* $1,500 \times (0.10 / 1M) = 0.00015 USD$
    *   *Output:* $6,000 \times (0.40 / 1M) = 0.00240 USD$
    *   **Tổng cộng:** **0.00255 USD (~64 VNĐ) / một hợp đồng được tạo**.

#### D. Tính năng: Tạo Ticket (Hỗ trợ chuyên gia - Expert Review)
*   Tính năng này bản chất là chuyển thông tin lỗi hoặc yêu cầu thẩm định hợp đồng nâng cao sang chuyên gia (Người thật) kiểm duyệt.
*   *Chi phí AI:* 0 VNĐ (hoặc chỉ mất rất ít ~10 VNĐ nếu dùng AI phân loại tự động ticket).
*   *Chi phí vận hành chính:* Trả lương cho Chuyên gia pháp lý / Luật sư cộng tác.

---

## 2. Đề Xuất Các Gói Dịch Vụ Subscription

Nhờ chi phí vận hành AI của Gemini vô cùng rẻ, bạn hoàn toàn có thể thiết lập mức giá subscription dễ chịu cho khách hàng nhưng vẫn giữ được tỷ suất lợi nhuận gộp (Gross Margin) cực kỳ cao (> 80% ở các gói trả phí).

Dưới đây là đề xuất chi tiết cho **3 gói dịch vụ**:

### Gói Trải Nghiệm: GÓI MIỄN PHÍ (FREE PLAN)
> [!NOTE]
> Gói dịch vụ mặc định được kích hoạt khi người dùng đăng ký tài khoản mới, nhằm giúp khách hàng dùng thử các tính năng phân tích và tư vấn cơ bản của ứng dụng trước khi quyết định nâng cấp gói trả phí.

*   **Giá đề xuất:** **0 VNĐ / tháng**
*   **Hạn mức (Quotas) hàng tháng:**
    *   **Phân tích hợp đồng:** Tối đa **5 hợp đồng** / tháng (khớp hoàn toàn với seed data mặc định).
    *   **Chat tư vấn với AI:** Tối đa **50.000 tokens** (~15 tin nhắn) / tháng.
    *   **Tạo/Soạn hợp đồng mới:** Tối đa **1 hợp đồng** / tháng.
    *   **Hỏi đáp chuyên gia (Expert Review):** Không hỗ trợ.
    *   **Số lượng Workspace tối đa:** **1 Workspace**.
    *   **Số lượng hợp đồng tối đa / Workspace:** **3 hợp đồng** (trên tổng số 5 hợp đồng phân tích tối đa).
*   **Phân tích chi phí:**
    *   *Chi phí API tối đa nếu user dùng hết 100% quota:*
        *   Phân tích: $5 \text{ hợp đồng} \times 23.25 \text{ VNĐ} = 116.25 \text{ VNĐ}$
        *   Chat RAG: $20 \times 11.5 \text{ VNĐ} = 230 \text{ VNĐ}$
        *   Soạn thảo: $1 \times 64 \text{ VNĐ} = 64 \text{ VNĐ}$
        *   **Tổng chi phí AI tối đa:** ~410 VNĐ / người dùng / tháng.
    *   *Đánh giá kinh doanh:* Chi phí rất thấp (~410 VNĐ / user tối đa công suất). Đây là chiến lược phễu Marketing hiệu quả để tăng tỷ lệ chuyển đổi khách hàng tiềm năng mà không lo rủi ro sập tài chính hạ tầng.

---

### Gói 1: GÓI TIÊU CHUẨN (STANDARD PLAN)
> [!NOTE]
> Gói dịch vụ hướng tới cá nhân, người dùng phổ thông, người đi thuê nhà/đất hoặc các freelancer cần kiểm tra giấy tờ pháp lý không quá thường xuyên.

*   **Giá đề xuất:** **79.000 VNĐ / tháng** (~ $3.2 USD)
*   **Hạn mức (Quotas) hàng tháng:**
    *   **Phân tích hợp đồng:** Tối đa **50 hợp đồng** / tháng.
    *   **Chat tư vấn với AI:** Tối đa **1.500.000 tokens** (~450 tin nhắn) / tháng.
    *   **Tạo/Soạn hợp đồng mới:** Tối đa **10 hợp đồng** / tháng.
    *   **Hỏi đáp chuyên gia (Expert Review):** Không hỗ trợ ticket miễn phí. Người dùng có thể mua thêm ticket lẻ (pay-as-you-go) với giá gốc **250.000 VNĐ / ticket**.
    *   **Số lượng Workspace tối đa:** **5 Workspace** (cho phép phân chia theo các danh mục như "Cá nhân", "Thuê nhà", "Freelance"...).
    *   **Số lượng hợp đồng tối đa / Workspace:** **15 hợp đồng** (hạn mức tối ưu nhằm duy trì độ chính xác cao của RAG).
*   **Phân tích chi phí & biên lợi nhuận:**
    *   *Chi phí API tối đa nếu user dùng hết 100% quota:*
        *   Phân tích: $50 \text{ hợp đồng} \times 23.25 \text{ VNĐ} = 1,163 \text{ VNĐ}$
        *   Chat RAG: $500 \times 11.5 \text{ VNĐ} = 5,750 \text{ VNĐ}$
        *   Soạn thảo: $10 \times 64 \text{ VNĐ} = 640 \text{ VNĐ}$
        *   **Tổng chi phí AI tối đa:** ~7,553 VNĐ / tháng.
    *   *Biên lợi nhuận gộp:* **~90.4%** (Đã tối ưu chi phí hạ tầng máy chủ, thanh toán gateway, cơ sở dữ liệu Neo4j/Postgres và chi phí marketing).

---

### Gói 2: GÓI CAO CẤP / CHUYÊN NGHIỆP (PREMIUM / PRO PLAN)
> [!IMPORTANT]
> Gói dịch vụ hướng tới văn phòng luật nhỏ, môi giới bất động sản chuyên nghiệp, nhân sự HR doanh nghiệp hoặc các chuyên gia pháp chế cần xử lý tài liệu liên tục với tần suất cao.

*   **Giá đề xuất:** **299.000 VNĐ / tháng** (~ $12 USD)
*   **Hạn mức (Quotas) hàng tháng:**
    *   **Phân tích hợp đồng:** Tối đa **200 hợp đồng** / tháng (đồng bộ với giá trị gieo mầm trong DB).
    *   **Chat tư vấn với AI:** Tối đa **8.500.000 tokens** (~2.500 tin nhắn) / tháng.
    *   **Tạo/Soạn hợp đồng mới:** Tối đa **40 hợp đồng** / tháng.
    *   **Hỏi đáp chuyên gia (Expert Review):** Tặng kèm **1 ticket miễn phí** / tháng (trị giá 250.000 VNĐ). Các ticket phát sinh thêm được giảm giá 20% (chỉ còn **200.000 VNĐ / ticket**).
    *   **Số lượng Workspace tối đa:** **20 Workspace** (đáp ứng nhu cầu quản lý nhiều khách hàng và dự án độc lập).
    *   **Số lượng hợp đồng tối đa / Workspace:** **50 hợp đồng** (hạn mức tối đa an toàn để đảm bảo tốc độ truy vấn và độ chính xác của AI).
*   **Các tính năng cộng thêm:**
    *   Xuất file báo cáo phân tích rủi ro sang định dạng PDF/Word.
    *   So sánh song song 2 phiên bản hợp đồng (Contract Comparison).
    *   Lưu trữ thư viện điều khoản tùy chỉnh cá nhân (Custom Clause Library).
*   **Phân tích chi phí & biên lợi nhuận:**
    *   *Chi phí tối đa nếu user dùng hết 100% quota và gửi 1 ticket:*
        *   Phân tích: $200 \text{ hợp đồng} \times 23.25 \text{ VNĐ} = 4,650 \text{ VNĐ}$
        *   Chat RAG: $2,500 \times 11.5 \text{ VNĐ} = 28,750 \text{ VNĐ}$
        *   Soạn thảo: $40 \times 64 \text{ VNĐ} = 2,560 \text{ VNĐ}$
        *   Trả chuyên gia (1 ticket): ~150.000 VNĐ (chi phí hoa hồng thực tế cho luật sư cộng tác)
        *   **Tổng chi phí vận hành tối đa:** ~185,960 VNĐ / tháng.
    *   *Biên lợi nhuận gộp:* **~37.8%** khi sử dụng 100% quota và gửi ticket hỗ trợ (mức an toàn cao). Nếu không gửi ticket chuyên gia, biên lợi nhuận gộp của AI đạt **~88.0%** (chỉ mất ~35.960 VNĐ tiền API).

---

## 3. Giới Hạn Quản Lý Dự Án & Hiệu Năng AI (Workspace & File Limits)

Việc giới hạn **số lượng Workspace tối đa** và **số lượng hợp đồng tối đa trong một Workspace** dựa theo các gói dịch vụ mang cả ý nghĩa thương mại và giải pháp kỹ thuật cần thiết để tối ưu hóa hệ thống:

### A. Tối ưu hóa độ chính xác của AI RAG (Retrieval-Augmented Generation)
*   **Bối cảnh:** Khi người dùng thực hiện tính năng **Chat với AI trong một Workspace**, hệ thống sẽ truy vấn Vector Database và Graph Database (Neo4j) để tìm kiếm các văn cảnh (Context) liên quan trong tất cả các hợp đồng nằm trong Workspace đó.
*   **Ảnh hưởng:** Nếu một Workspace chứa quá nhiều hợp đồng khác nhau (ví dụ: hợp đồng thuê nhà của 10 đối tác khác nhau), AI sẽ dễ bị nhiễu thông tin (hallucination) do thông tin các hợp đồng bị lẫn lộn khi tìm kiếm tương đồng.
*   **Giải pháp:** Giới hạn số lượng hợp đồng tối đa trong một Workspace (3 đối với FREE, 15 đối với STANDARD, 50 đối với PREMIUM) buộc người dùng phân loại tài liệu thành các Workspace riêng biệt (ví dụ: mỗi khách hàng hoặc mỗi dự án là một Workspace riêng). Điều này đảm bảo AI luôn lấy đúng ngữ cảnh và đưa ra câu trả lời chính xác nhất.

### B. Hiệu năng truy vấn & Tránh lạm dụng hệ thống
*   **Tốc độ phản hồi (Latency):** Giới hạn dung lượng dữ liệu trong một Workspace giúp các câu lệnh truy vấn Vector / Graph DB diễn ra nhanh chóng, đảm bảo phản hồi chat của AI luôn dưới 2-3 giây.
*   **Kiểm soát tài nguyên (Resource Control):** Giới hạn số lượng Workspace ngăn người dùng tạo hàng loạt Workspace rác, giảm tải cho cơ sở dữ liệu metadata trong backend.

---

## 4. Khuyến Nghị Cấu Hình & Tích Hợp Vào Hệ Thống Hiện Tại

Trong dự án của bạn, cơ sở dữ liệu đã có sẵn bảng cấu hình cho `subscription_plans`. Bạn có thể dễ dàng đồng bộ các gói này thông qua class [DataInitializer.java](file:///d:/University/SU26/SBA301/ai-legal-document-analyzer/backend/src/main/java/com/analyzer/api/config/DataInitializer.java).

Để hệ thống phân biệt rõ và kiểm soát các giới hạn mới (Workspace và Hợp đồng tối đa), bạn có hai giải pháp triển khai:

### Giải pháp 1: Thêm trực tiếp các trường vào Entity `SubscriptionPlan` (Khuyên dùng)
Thêm các cột mới vào bảng `subscription_plans` trong [SubscriptionPlan.java](file:///d:/University/SU26/SBA301/ai-legal-document-analyzer/backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java):
*   `maxWorkspaces`: Số lượng workspace tối đa người dùng được tạo.
*   `maxContractsPerWorkspace`: Số lượng hợp đồng tối đa trong một workspace.

### Giải pháp 2: Sử dụng trường `featureLimitsJson` có sẵn
Trường `feature_limits_json` trong Entity [SubscriptionPlan.java](file:///d:/University/SU26/SBA301/ai-legal-document-analyzer/backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java) đã được định cấu hình dạng TEXT. Bạn có thể lưu các giới hạn nâng cao dưới dạng JSON:
*   *Gói Miễn Phí:* `{"max_workspaces": 1, "max_contracts_per_workspace": 3}`
*   *Gói Tiêu Chuẩn:* `{"max_workspaces": 5, "max_contracts_per_workspace": 15}`
*   *Gói Cao Cấp:* `{"max_workspaces": 20, "max_contracts_per_workspace": 50}`

---

### Gợi ý cấu hình dữ liệu gieo mầm (Seed Data Java) - Theo Giải pháp 1:

```java
// Gói Miễn Phí
SubscriptionPlan freePlan = SubscriptionPlan.builder()
        .planName("Gói Miễn Phí")
        .planType("FREE")
        .description("Gói cơ bản trải nghiệm dịch vụ phân tích văn bản pháp lý và AI tư vấn")
        .price(BigDecimal.ZERO)
        .durationDays(30)
        .maxQuota(5)        // 5 lượt phân tích hợp đồng
        .aiQuota(50000)     // 50,000 tokens chat AI
        .ticketQuota(0)     // Không hỗ trợ ticket chuyên gia
        .maxWorkspaces(1)               // Giới hạn 1 Workspace
        .maxContractsPerWorkspace(3)    // Giới hạn 3 hợp đồng / Workspace
        .active(true)
        .build();

// Gói Tiêu Chuẩn
SubscriptionPlan standardPlan = SubscriptionPlan.builder()
        .planName("Gói Tiêu Chuẩn")
        .planType("MONTHLY")
        .tier(SubscriptionTier.BASIC) // Map với Basic Tier
        .description("Gói tiêu chuẩn cho cá nhân, truy cập nhiều lượt phân tích và chat")
        .price(new BigDecimal("79000"))
        .durationDays(30)
        .maxQuota(50)       // 50 lượt phân tích hợp đồng
        .aiQuota(1500000)   // 1,500,000 tokens chat AI
        .ticketQuota(0)     // Không hỗ trợ ticket chuyên gia miễn phí
        .maxWorkspaces(5)               // Giới hạn 5 Workspace
        .maxContractsPerWorkspace(15)   // Giới hạn 15 hợp đồng / Workspace
        .active(true)
        .build();

// Gói Cao Cấp
SubscriptionPlan premiumPlan = SubscriptionPlan.builder()
        .planName("Gói Cao Cấp")
        .planType("MONTHLY")
        .tier(SubscriptionTier.PRO) // Map với Pro Tier
        .description("Gói cao cấp cho chuyên gia, mở khóa toàn bộ tính năng và ticket chuyên gia")
        .price(new BigDecimal("299000"))
        .durationDays(30)
        .maxQuota(200)      // 200 lượt phân tích hợp đồng
        .aiQuota(8500000)   // 8,500,000 tokens chat AI
        .ticketQuota(1)     // 1 ticket hỗ trợ chuyên gia miễn phí
        .maxWorkspaces(20)              // Giới hạn 20 Workspace
        .maxContractsPerWorkspace(50)   // Giới hạn 50 hợp đồng / Workspace
        .active(true)
        .build();
```

Sử dụng cấu hình này giúp dự án kiểm soát tài nguyên hệ thống hiệu quả, tối ưu hóa chi phí vận hành hạ tầng đồng thời đảm bảo chất lượng dịch vụ AI tốt nhất cho từng phân khúc khách hàng.
