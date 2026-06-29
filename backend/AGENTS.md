# AGENTS.md

Huong dan nay danh cho agent lam viec trong du an backend Java Spring Boot.
Doc file nay truoc khi sua code de giu thay doi dung cau truc hien co.

## Tong quan du an

- Backend cua he thong AI Legal Document Analyzer.
- Framework: Spring Boot 4.0.6, Java 21, Maven Wrapper.
- Database: PostgreSQL, Spring Data JPA, Hibernate `ddl-auto: update`.
- Bao mat: Spring Security, JWT access token va refresh token.
- Mapping: MapStruct + Lombok.
- API docs: springdoc OpenAPI, Swagger UI tai `/swagger-ui.html`.
- Package goc: `com.analyzer.api`.

## Lenh thuong dung

Chay trong thu muc backend:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd clean package
.\mvnw.cmd test
docker compose up --build
```

Ung dung mac dinh chay port `8080`.
PostgreSQL trong `docker-compose.yml` chay port `5432`.
AI service mac dinh duoc cau hinh tai `app.ai-service.base-url`.

## Cau truc thu muc

```text
src/main/java/com/analyzer/api/
  LegalAnalyzerApplication.java
  client/       Client goi service ngoai, vi du PythonAiClient
  config/       Cau hinh Spring, OpenAPI, properties, data seed
  controller/   REST controller va Swagger annotations
  dto/          Request/response DTO theo tung domain
  entity/       JPA entities
  enums/        Enum trang thai, role, payment, ticket, chat
  exception/    Custom exceptions va GlobalExceptionHandler
  mapper/       MapStruct mapper
  repository/   Spring Data JPA repositories
  security/     JWT, SecurityConfig, UserDetails
  service/      Service interfaces
  service/impl/ Service implementations
src/main/resources/
  application.yml
docs/
  Dac ta nghiep vu va API bo sung
```

## Quy uoc them/sua feature

- Giu package theo tung tang hien co: `controller -> service interface -> service/impl -> repository/entity`.
- Controller chi xu ly HTTP, auth context, validate input co ban va goi service.
- Business logic nam trong `service.impl`, co `@Service`, `@RequiredArgsConstructor`, va `@Transactional` khi ghi du lieu.
- Neu them API moi, tra ve `ResponseEntity<ApiResponseDTO<...>>` va dung factory method `success`, `created`, `accepted`, `error`.
- Request DTO dat trong `dto/<domain>/`, dung Jakarta Validation (`@Valid`, `@NotBlank`, `@Size`, v.v.) khi co the.
- Response DTO nen dung builder/Lombok hoac record theo style file xung quanh.
- Repository nen la interface Spring Data JPA, uu tien method query ro rang truoc khi viet custom query.
- Mapper dung MapStruct voi `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)` neu domain da co mapper.
- ID dang string trong nhieu domain co prefix, vi du `chat_...`; neu them entity tuong tu, giu style prefix ro nghia.

## API va bao mat

- Duong dan API hien theo style `/api/v1/...`.
- Controller dung `@PreAuthorize` theo role khi endpoint can bao ve, vi du `hasRole('CUSTOMER')`.
- Lay user hien tai qua `SecurityContextHolder` va `UserDetailsImpl` theo pattern controller hien co.
- Khong hard-code quyen truy cap trong controller neu logic can kiem tra owner/resource; thuc hien trong service.
- Khi them endpoint, cap nhat Swagger annotations `@Tag` va `@Operation` neu controller cung dang dung.

## Exception va response loi

- Dung custom exception trong `exception/common`, `exception/validation`, `exception/chat`, `exception/workspace`, `exception/ai` thay vi nem `RuntimeException` chung chung.
- Neu them exception moi, them handler tuong ung trong `GlobalExceptionHandler`.
- Loi validate request body nen de `MethodArgumentNotValidException` xu ly khi co the.
- Loi phan quyen dung `ForbiddenException`; loi khong tim thay dung `ResourceNotFoundException`; loi xung dot dung `ConflictException`.

## Cau hinh va bien moi truong

- Cau hinh chinh nam trong `src/main/resources/application.yml`.
- Du an co dependency `spring-dotenv`, co the doc `.env`.
- Khong commit secret thuc te. Neu can them cau hinh moi, dung placeholder `${ENV_NAME:default}`.
- Cac cau hinh dang co:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - `app.ai-service.base-url`
  - `app.jwt.*`
  - `app.payment.vnpay.*`
  - `app.storage.upload-root`

## Database va entity

- Entity nam trong `entity/`, repository nam trong `repository/`.
- Can than voi quan he JPA de tranh lazy loading ngoai transaction va JSON recursion.
- Neu entity co soft delete/status, giu logic loc status trong service/repository thay vi xoa vat ly.
- Khi them enum trang thai moi, kiem tra controller parsing, validation exception, service transition va docs.

## Test va kiem tra

- Hien chua thay test source trong `src/test`; neu sua logic quan trong, nen them test phu hop.
- Truoc khi ket thuc thay doi backend, chay toi thieu:

```powershell
.\mvnw.cmd test
```

- Neu thay doi mapping/annotation processor, chay:

```powershell
.\mvnw.cmd clean package
```

## Tai lieu lien quan

- Doc `docs/legal_ticket_specification.md` va `docs/legal_ticket_extended_specification.md` truoc khi sua luong legal ticket.
- Thu muc `historycode/` la ghi chu lich su; khong sua neu task khong yeu cau.
- Khong sua file ngoai backend neu user chi yeu cau backend.

## Nguyen tac lam viec

- Giu thay doi nho, dung pham vi yeu cau.
- Khong format lai toan bo file neu chi sua logic nho.
- Khong revert thay doi khong phai do minh tao.
- Neu gap secrets, thong tin thanh toan, JWT hoac cau hinh production, xu ly can than va hoi lai neu khong chac.
- Khi them dependency Maven moi, dam bao no can thiet va khong trung voi thu vien san co.
