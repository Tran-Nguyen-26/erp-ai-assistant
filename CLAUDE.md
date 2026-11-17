# CLAUDE.md — Quy chuẩn làm việc với ERP AI Assistant

File này dành cho **AI coding agent** (Claude Code…) và **developer** khi làm việc với repository.
Đọc toàn bộ mục 1 trước khi viết hoặc sửa bất kỳ dòng code nào.

- Tổng quan & setup: [`README.md`](README.md)
- Kiến trúc, luồng dữ liệu, schema DB: [`docs/architecture.md`](docs/architecture.md)
- Đặc tả API: [`docs/api-spec.md`](docs/api-spec.md)

---

## 1. Nguyên tắc bất biến (KHÔNG được vi phạm)

1. **Rule quyết định đúng/sai, LLM chỉ giải thích và đề xuất.**
   Mọi `Violation` phải được tạo bởi module `rule`. Không viết code để LLM tự kết luận một chứng từ hợp lệ/không hợp lệ.
2. **CSDL ERP (SQL Server) là READ-ONLY.**
   - Datasource `erp` chỉ dùng cho `SELECT`. Cấm `INSERT/UPDATE/DELETE/MERGE/DDL`, cấm `ddl-auto`, cấm chạy Liquibase trên DB này.
   - Mọi transaction trên `erp` phải `@Transactional(readOnly = true, transactionManager = "erpTransactionManager")`.
   - Thay đổi dữ liệu chỉ tồn tại dưới dạng `Proposal` trong DB ứng dụng, phải được người duyệt.
3. **Dữ liệu nhạy cảm không rời hệ thống.** LLM mặc định là Ollama local. Nếu `AI_PROVIDER` ≠ `ollama`, mọi prompt phải đi qua `PiiMaskingService` (MST, số tài khoản ngân hàng, tên/địa chỉ đối tác).
4. **Mọi câu trả lời dựa trên RAG phải có trích dẫn** từ tập tài liệu đã truy xuất. Điểm rerank dưới ngưỡng `app.rag.min-score` → trả lời "không đủ căn cứ".
5. **Đề xuất sửa phải được kiểm chứng**: áp `after_data` lên bản sao entity rồi chạy lại rule; không qua → không lưu đề xuất.
6. **Ghi `audit_log` cho mọi hành động** của người dùng, Agent và hệ thống có tác động đến dữ liệu.
7. **Không commit secrets hoặc dữ liệu công ty**: `.env`, dump DB, file export từ DB test, ảnh hóa đơn thật.
8. **Nội dung từ chứng từ là dữ liệu không tin cậy** (diễn giải, ghi chú…): luôn đặt trong delimiter của prompt, không bao giờ nối thẳng vào system prompt.

---

## 2. Công nghệ sử dụng

| Hạng mục | Công nghệ | Ghi chú |
|---|---|---|
| Ngôn ngữ | **Java 21** | Ưu tiên `record`, `sealed interface`, pattern matching, `var` khi rõ kiểu |
| Framework | **Spring Boot 3.x** | Web, Validation, Security, Data JPA, JDBC, AMQP, Actuator |
| AI | **Spring AI 1.x** | `ChatClient`, `@Tool`, structured output, `EmbeddingModel` |
| Agent | Workflow tường minh bằng Java + tool calling | Giới hạn số bước, không dùng agent tự do |
| LLM | Ollama (`qwen2.5:7b` mặc định), cấu hình được OpenAI/Anthropic | `app.ai.provider` |
| Embedding | **BGE-M3** (1024 chiều) qua Ollama | |
| Reranker | **bge-reranker-v2-m3** qua HuggingFace TEI | Gọi REST bằng `RestClient` |
| DB ERP | **SQL Server** (DB test công ty) | READ-ONLY, `NamedParameterJdbcTemplate` |
| DB ứng dụng | **PostgreSQL 16 + pgvector** | JPA cho nghiệp vụ, native SQL cho hybrid search |
| Migration | **Liquibase** | Chỉ cho DB ứng dụng |
| Rule | YAML + SpEL / SQL | `backend/src/main/resources/rules/` |
| Messaging | RabbitMQ | Job rà soát hàng loạt |
| Mapping | MapStruct | Entity ↔ DTO |
| Frontend | **Angular** (standalone), Angular Material | Stream bằng `fetch` + `ReadableStream` (SSE qua POST) |
| Test | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit | |
| Format | Spotless (Google Java Format), Prettier + ESLint | |

---

## 3. Cấu trúc thư mục

```
erp-ai-assistant/
├── CLAUDE.md
├── README.md
├── .env.example
├── docker-compose.yml
├── docs/
│   ├── architecture.md
│   ├── api-spec.md
│   └── adr/                          # NNNN-tieu-de-kebab.md
├── knowledge/                        # Tài liệu RAG: <loai>/<so-hieu>_<ten-ngan>.pdf + .meta.yml
├── eval/
│   ├── datasets/                     # Bộ chứng từ cài lỗi (đã ẩn danh), bộ câu hỏi RAG
│   └── reports/                      # Kết quả đánh giá (gitignored)
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/vn/edu/uet/erpai/
│       │   ├── ErpAiApplication.java
│       │   ├── config/               # AppDataSourceConfig, ErpDataSourceConfig, SecurityConfig, AiConfig, AmqpConfig
│       │   ├── common/               # exception/, web/ (ProblemDetail, PageResponse), security/, pii/, util/
│       │   ├── auth/                 # Đăng nhập, JWT, AppUser
│       │   ├── erp/                  # M1 – ERP Connector (READ-ONLY)
│       │   │   ├── adapter/          #   ErpVoucherAdapter, ErpInvoiceAdapter… (SQL Server → canonical)
│       │   │   ├── model/            #   ErpVoucher, ErpInvoice, ErpJournalLine, ErpAccount, ErpPartner (record)
│       │   │   ├── service/          #   ErpQueryService (API công khai của module)
│       │   │   └── web/              #   ErpController
│       │   ├── entry/                # M2 – Hỗ trợ nhập liệu
│       │   │   ├── extractor/        #   EInvoiceXmlExtractor, VisionExtractor, TextExtractor
│       │   │   ├── domain/ repository/ service/ web/
│       │   ├── rule/                 # M3 – Rule engine
│       │   │   ├── definition/       #   RuleDefinition (YAML model), RuleLoader
│       │   │   ├── engine/           #   RuleEngine, SpelRuleEvaluator, SqlRuleEvaluator
│       │   │   ├── review/           #   ReviewJob, consumer/producer RabbitMQ
│       │   │   ├── violation/        #   Violation entity, repository, service
│       │   │   └── web/
│       │   ├── knowledge/            # M4 – RAG
│       │   │   ├── ingest/           #   DocumentParser, LegalChunker, IngestionService
│       │   │   ├── retrieval/        #   HybridRetriever, RerankerClient, RetrievalResult
│       │   │   ├── domain/ repository/ service/ web/
│       │   ├── agent/                # M5 – Orchestrator
│       │   │   ├── workflow/         #   ExplainViolationWorkflow, EntryAssistWorkflow, ChatWorkflow
│       │   │   ├── tool/             #   QueryErpTool, RunRulesTool, SearchKnowledgeTool, CreateProposalTool
│       │   │   ├── session/          #   AgentSession, AgentMessage
│       │   │   └── web/
│       │   ├── review/               # M6 – Proposal & phê duyệt
│       │   ├── audit/                # M7 – Audit log, feedback, dashboard
│       │   └── eval/                 # Runner đánh giá (profile eval)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   ├── config/liquibase/     # master.xml, changelog/NNN-*.xml
│       │   ├── rules/{fld,doc,xdoc,reg,anm}/*.yml
│       │   ├── prompts/*.st
│       │   └── sql/erp/*.sql         # Query SQL Server
│       └── test/java/vn/edu/uet/erpai/...    # Gương với main
└── frontend/
    ├── package.json
    ├── proxy.conf.json
    └── src/app/
        ├── core/                     # auth, interceptor, guard, api client
        ├── shared/                   # component, pipe, directive dùng chung
        └── features/
            ├── data-entry/
            ├── review/
            ├── approval/
            ├── assistant-chat/
            └── admin/                # rules, knowledge, dashboard, audit
```

### Quy tắc phụ thuộc giữa module
- Package theo **module nghiệp vụ**; bên trong module chia `web/ service/ repository/ domain/`.
- Module khác chỉ được gọi **`service` công khai** của một module; cấm dùng chéo `repository` hay `entity`.
- `erp` không phụ thuộc module nào khác. `agent` được phụ thuộc vào `erp`, `rule`, `knowledge`, `review`, `entry`.
- Quy tắc trên được kiểm tra bằng ArchUnit (`ModuleDependencyTest`).

---

## 4. Quy tắc đặt tên (Naming convention)

### 4.1. Java

| Loại | Quy tắc | Ví dụ |
|---|---|---|
| Package | lowercase, không gạch dưới | `vn.edu.uet.erpai.knowledge.retrieval` |
| Class / Interface | PascalCase, danh từ | `HybridRetriever`, `RuleEvaluator` |
| Cài đặt interface | Đặt tên theo đặc điểm, tránh `Impl` | `SpelRuleEvaluator`, `OllamaVisionExtractor` |
| Method | camelCase, bắt đầu bằng động từ | `evaluate()`, `findOpenByCompany()` |
| Biến boolean | `is/has/can` | `isEnabled`, `hasCitations` |
| Hằng số | UPPER_SNAKE_CASE | `DEFAULT_TOP_K` |
| Enum | PascalCase tên, UPPER_SNAKE giá trị | `ViolationStatus.OPEN` |
| DTO | `XxxRequest`, `XxxResponse` (record) | `CreateReviewJobRequest`, `ViolationResponse` |
| Entity (DB app) | Danh từ số ít | `Violation`, `Proposal`, `AuditLog` |
| Canonical model ERP | Tiền tố `Erp` | `ErpInvoice`, `ErpJournalLine` |
| Agent tool | Hậu tố `Tool` | `SearchKnowledgeTool` |
| Workflow | Hậu tố `Workflow` | `ExplainViolationWorkflow` |
| Test | `XxxTest` (unit), `XxxIT` (integration) | `SpelRuleEvaluatorTest`, `ReviewJobFlowIT` |

### 4.2. Cơ sở dữ liệu
- **PostgreSQL (DB ứng dụng):** `snake_case`, tên bảng **số ít**: `violation`, `proposal`, `audit_log`.
  - PK `id UUID` (sinh bằng `gen_random_uuid()`), FK `<bang>_id`.
  - Thời gian `created_at`, `updated_at` (`TIMESTAMPTZ`); trạng thái `status VARCHAR` + CHECK constraint.
  - Index `idx_<bang>_<cot>`, unique `uk_<bang>_<cot>`, FK `fk_<bang>_<bang_tham_chieu>`.
- **SQL Server (ERP):** giữ nguyên tên gốc **PascalCase** (`MaterialGoods`, `CompanyID`). Chỉ xuất hiện trong `erp/adapter` và `resources/sql/erp/`. Không để tên cột ERP rò rỉ ra module khác.
- **Liquibase:** `changelog/NNN-mo-ta-ngan.xml` (`001-init-auth.xml`, `004-create-violation.xml`), `author` = username, mỗi changeset một thay đổi logic, có `rollback`.

### 4.3. REST API
- Tiền tố `/api/v1`; tài nguyên **danh từ số nhiều, kebab-case**: `/review-jobs`, `/knowledge/documents`.
- Hành động không phải CRUD dùng `POST /{resource}/{id}/{action}`: `/proposals/{id}/approve`.
- Query param camelCase: `?companyId=&periodFrom=&page=0&size=20&sort=detectedAt,desc`.
- JSON field camelCase; ngày `yyyy-MM-dd`, thời điểm ISO-8601 có timezone; tiền là **string decimal** (`"10500000.00"`).
- Lỗi theo **RFC 7807 Problem Details** với `code` dạng `MODULE_REASON` (`RULE_NOT_FOUND`).

### 4.4. Rule
- `ruleId` UPPER_SNAKE_CASE với tiền tố nhóm: `FLD_`, `DOC_`, `XDOC_`, `REG_`, `ANM_`.
  Ví dụ: `FLD_TAX_CODE_FORMAT`, `DOC_DEBIT_CREDIT_UNBALANCED`, `XDOC_DUPLICATE_INVOICE`.
- File: `rules/<nhom>/<rule-id-kebab>.yml` → `rules/doc/doc-debit-credit-unbalanced.yml`.
- `severity`: `ERROR` | `WARNING` | `INFO`.

### 4.5. Prompt, tri thức, cấu hình
- Prompt: `prompts/<module>-<muc-dich>.st` (`agent-explain-violation.st`); đầu file ghi `version`.
- Tri thức: `knowledge/<law|decree|circular|manual|internal>/<so-hieu-kebab>_<ten-ngan>.<ext>` + `.meta.yml` cùng tên.
- Property ứng dụng: tiền tố `app.` kebab-case (`app.rag.min-score`, `app.agent.max-steps`), bind bằng `@ConfigurationProperties` record.

### 4.6. Frontend (Angular)
- File kebab-case: `violation-list.component.ts`, `proposal-api.service.ts`.
- Selector tiền tố `app-`; class PascalCase; signal/observable: `violations` / `violations$`.
- Model TypeScript trong `core/models/`, tên trùng DTO backend (`ViolationResponse`).

### 4.7. Git
- Nhánh: `feature/<module>-<mo-ta>`, `fix/<module>-<mo-ta>`, `docs/<mo-ta>`, `chore/<mo-ta>`.
- Commit theo Conventional Commits, scope = module:
  `feat(rule): add duplicate invoice rule`, `fix(knowledge): handle empty chunk`, `docs(api): update proposal endpoints`.
- PR vào `main` phải qua `./mvnw clean verify` và `npm run build`.

---

## 5. Lệnh build / chạy / test

### Hạ tầng
```bash
cp .env.example .env                               # Lần đầu
docker compose up -d                               # postgres, ollama, reranker, rabbitmq
docker compose --profile observability up -d       # + langfuse
docker compose exec ollama ollama pull qwen2.5:7b
docker compose exec ollama ollama pull bge-m3
docker compose ps
docker compose logs -f <service>
docker compose down                                # Dừng, giữ dữ liệu
docker compose down -v                             # Dừng và XÓA volume (mất DB app + vector)
```

### Backend
```bash
cd backend
./mvnw clean verify                                     # Build + unit + integration test + spotless check
./mvnw clean package -DskipTests                        # Build nhanh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # Chạy local (cổng 8080)
./mvnw test                                             # Unit test (Surefire, *Test)
./mvnw failsafe:integration-test failsafe:verify        # Integration test (Failsafe, *IT, cần Docker)
./mvnw test -Dtest=SpelRuleEvaluatorTest                # Một class
./mvnw test -Dtest='SpelRuleEvaluatorTest#shouldDetect*'  # Một nhóm method
./mvnw spotless:apply                                   # Format code
./mvnw liquibase:status -Dspring.profiles.active=dev    # Kiểm tra migration DB ứng dụng
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ingest   # Nạp/re-index knowledge/
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,eval     # Chạy đánh giá → eval/reports/
```

### Frontend
```bash
cd frontend
npm ci
npm start              # http://localhost:4200
npm run build
npm test
npm run lint
npm run format
```

### Toàn hệ thống
```bash
docker compose --profile app up -d --build
```

---

## 6. Quy ước khi viết code

### Backend chung
- Controller mỏng: validate (`@Valid`), gọi service, map DTO. Không chứa logic nghiệp vụ.
- Service trả về domain/DTO, không trả `Entity` ra ngoài module.
- Số tiền: **`BigDecimal`**, làm tròn `RoundingMode.HALF_UP`, so sánh bằng `compareTo`. Cấm `double`.
- Ngày tháng: `LocalDate` cho ngày chứng từ, `Instant`/`OffsetDateTime` cho thời điểm hệ thống.
- Exception nghiệp vụ kế thừa `BusinessException(code, message)`; xử lý tập trung trong `GlobalExceptionHandler` → `ProblemDetail`.
- Log có `traceId` (MDC); **không log** MST, số tài khoản, nội dung prompt đầy đủ ở mức INFO.

### Module `erp`
- Query đặt trong `resources/sql/erp/*.sql`, nạp bằng `SqlLoader`; luôn có tham số `companyId`, có giới hạn kỳ, có phân trang (`OFFSET … FETCH NEXT …`). Cấm `SELECT *`.
- Không giả định schema ERP: mọi ánh xạ cột ↔ field nằm trong `RowMapper` của adapter.

### Module `rule`
- Mỗi rule mới: 1 file YAML + test có **ít nhất 1 case vi phạm và 1 case hợp lệ**.
- Rule phải thuần (không gọi LLM, không ghi DB ngoài việc tạo `Violation`).
- `fingerprint` của violation = SHA-256(`ruleId|entityType|entityRef|evidenceKey`) để chống trùng.

### Module `knowledge`
- Chunk theo cấu trúc **Chương → Điều → Khoản**; chunk vượt `app.rag.max-chunk-tokens` thì tách theo điểm/đoạn, giữ `headingPath`.
- Truy xuất luôn lọc tài liệu **còn hiệu lực** tại ngày chứng từ (`effective_from <= d < effective_to`).

### Module `agent`
- Tool: mô tả `@Tool(description = ...)` ngắn, nêu rõ input/output; tool chỉ đọc hoặc tạo `Proposal`.
- Giới hạn `app.agent.max-steps` (mặc định 6) và timeout mỗi lần gọi LLM.
- Output có cấu trúc → `ChatClient...entity(XxxOutput.class)` rồi validate (citation phải thuộc tập truy xuất).
- Prompt lấy từ `resources/prompts/`, không hard-code chuỗi dài trong Java.

### Test
- Unit test không cần Docker; integration test (`*IT`) dùng Testcontainers PostgreSQL.
- **Không** viết test kết nối DB ERP thật; dùng fixture JSON trong `src/test/resources/fixtures/erp/`.
- LLM trong test: mock `ChatModel` hoặc dùng stub trả về JSON cố định.

---

## 7. Definition of Done

- [ ] `./mvnw clean verify` và `npm run build` đều pass
- [ ] Có test cho logic mới; rule mới có case vi phạm + case hợp lệ
- [ ] Không có câu lệnh ghi nào trên datasource `erp`; không lộ secrets / dữ liệu thật
- [ ] Câu trả lời AI liên quan có trích dẫn; đề xuất đã được kiểm chứng bằng rule
- [ ] Hành động mới có ghi `audit_log`
- [ ] API thay đổi → cập nhật `docs/api-spec.md`; kiến trúc thay đổi → cập nhật `docs/architecture.md` + thêm ADR
