# Đặc tả RESTful API — ERP AI Assistant

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản API | `v1` |
| Base URL (dev) | `http://localhost:8080/api/v1` |
| Định dạng | JSON (UTF-8); upload `multipart/form-data`; stream `text/event-stream` |
| Xác thực | Bearer JWT |
| OpenAPI | Sinh tự động bởi springdoc: `/v3/api-docs`, Swagger UI: `/swagger-ui.html` (chỉ profile `dev`) |

---

## Mục lục

1. [Quy ước chung](#1-quy-ước-chung)
2. [Auth](#2-auth)
3. [ERP (chỉ đọc)](#3-erp-chỉ-đọc)
4. [Entry — Hỗ trợ nhập liệu](#4-entry--hỗ-trợ-nhập-liệu)
5. [Rules](#5-rules)
6. [Reviews & Review Jobs](#6-reviews--review-jobs)
7. [Violations](#7-violations)
8. [Proposals](#8-proposals)
9. [Knowledge](#9-knowledge)
10. [Assistant (chat)](#10-assistant-chat)
11. [Feedback, Audit, Dashboard](#11-feedback-audit-dashboard)
12. [Ma trận phân quyền](#12-ma-trận-phân-quyền)
13. [Danh mục enum](#13-danh-mục-enum)
14. [Danh mục mã lỗi](#14-danh-mục-mã-lỗi)

---

## 1. Quy ước chung

### 1.1. Header

| Header | Bắt buộc | Mô tả |
|---|---|---|
| `Authorization: Bearer <accessToken>` | Có (trừ `/auth/login`, `/auth/refresh`) | JWT |
| `Content-Type` | Khi có body | `application/json` hoặc `multipart/form-data` |
| `Accept-Language` | Không | `vi` (mặc định) \| `en` — ngôn ngữ thông báo lỗi |
| `X-Request-Id` | Không | Client gửi để truy vết; server trả lại cùng giá trị (hoặc tự sinh) |
| `Idempotency-Key` | Khuyến nghị cho `POST` tạo job/đề xuất | Chống tạo trùng khi client retry |

### 1.2. Kiểu dữ liệu

| Kiểu | Định dạng | Ví dụ |
|---|---|---|
| ID | UUID v4 | `"7c9e6679-7425-40de-944b-e07fc1f90ae7"` |
| Tham chiếu ERP | string (khóa bên ERP) | `"entityRef": "a1b2c3..."` |
| Ngày | `yyyy-MM-dd` | `"2026-10-31"` |
| Thời điểm | ISO-8601 có offset | `"2026-11-16T17:20:00+07:00"` |
| Tiền | **string** decimal, 2 chữ số thập phân | `"10500000.00"` |
| Tỷ lệ | number | `0.1` (10%) |

### 1.3. Phân trang, sắp xếp, lọc

Request: `?page=0&size=20&sort=detectedAt,desc&sort=severity,asc` (`size` tối đa 100).

Response dạng trang:

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalItems": 137,
  "totalPages": 7
}
```

### 1.4. Phản hồi lỗi (RFC 7807 Problem Details)

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json
```
```json
{
  "type": "https://erpai.local/errors/validation",
  "title": "Dữ liệu không hợp lệ",
  "status": 422,
  "detail": "Kỳ rà soát không được vượt quá 12 tháng",
  "instance": "/api/v1/review-jobs",
  "code": "REVIEW_PERIOD_TOO_LONG",
  "traceId": "4bf92f3577b34da6",
  "errors": [
    { "field": "periodTo", "message": "Phải cách periodFrom không quá 12 tháng" }
  ]
}
```

| HTTP | Khi nào |
|---|---|
| 200 | Thành công (GET, PUT, PATCH, action đồng bộ) |
| 201 | Tạo tài nguyên — kèm header `Location` |
| 202 | Đã nhận, xử lý bất đồng bộ (review job, ingestion) |
| 204 | Thành công, không có body (DELETE) |
| 400 | Request sai cú pháp |
| 401 | Thiếu/sai token |
| 403 | Không đủ quyền hoặc khác `companyId` |
| 404 | Không tìm thấy |
| 409 | Xung đột trạng thái (duyệt đề xuất đã duyệt, trùng idempotency key) |
| 413 | File quá lớn |
| 422 | Vi phạm ràng buộc nghiệp vụ / validation |
| 502 | Lỗi dịch vụ phụ thuộc (LLM, reranker, ERP DB) |
| 504 | Hết thời gian chờ dịch vụ phụ thuộc |

### 1.5. Phạm vi dữ liệu theo công ty
Mọi API dữ liệu đều lọc theo `companyId` của người dùng đăng nhập. Tham số `companyId` (nếu có) chỉ được chấp nhận khi trùng với công ty của user hoặc user là `ADMIN`; nếu không → `403 COMPANY_ACCESS_DENIED`.

---

## 2. Auth

### `POST /auth/login`
```json
// Request
{ "username": "chief", "password": "chief@123" }
```
```json
// 200 OK
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "0b6f...",
    "username": "chief",
    "fullName": "Nguyễn Văn A",
    "role": "CHIEF_ACCOUNTANT",
    "companyId": "3756878"
  }
}
```
Lỗi: `401 AUTH_INVALID_CREDENTIALS`, `403 AUTH_USER_DISABLED`.

### `POST /auth/refresh`
```json
{ "refreshToken": "eyJhbGciOi..." }
```
→ `200` cùng cấu trúc với login (không có `user`).

### `GET /auth/me`
→ `200` đối tượng `user` như trên.

---

## 3. ERP (chỉ đọc)

### `GET /erp/vouchers`
Tìm chứng từ trong ERP.

| Query | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `periodFrom`, `periodTo` | date | Có | Tối đa 12 tháng |
| `voucherType` | enum `VoucherType` | Không | |
| `q` | string | Không | Số chứng từ / diễn giải / tên đối tượng |
| `partnerTaxCode` | string | Không | |
| `hasViolations` | boolean | Không | Chỉ chứng từ có violation `OPEN` |
| `page`, `size`, `sort` | | Không | `sort` hỗ trợ `voucherDate`, `voucherNo`, `totalAmount` |

```json
// 200 OK
{
  "items": [
    {
      "entityRef": "a1b2c3d4",
      "entityType": "PURCHASE_INVOICE",
      "voucherNo": "MH00123",
      "voucherDate": "2026-10-15",
      "description": "Mua bàn phím văn phòng",
      "partner": { "code": "NCC001", "name": "Công ty TNHH A", "taxCode": "0101234567" },
      "totalAmount": "11000000.00",
      "currency": "VND",
      "openViolationCount": 2,
      "maxSeverity": "ERROR"
    }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

### `GET /erp/vouchers/{entityRef}`
Chi tiết chứng từ (canonical model) kèm dòng hạch toán và hóa đơn.

```json
{
  "entityRef": "a1b2c3d4",
  "entityType": "PURCHASE_INVOICE",
  "voucherNo": "MH00123",
  "voucherDate": "2026-10-15",
  "postedDate": "2026-10-15",
  "description": "Mua bàn phím văn phòng",
  "partner": { "code": "NCC001", "name": "Công ty TNHH A", "taxCode": "0101234567" },
  "currency": "VND",
  "exchangeRate": 1,
  "invoice": {
    "invoiceTemplate": "1",
    "invoiceSeries": "C26TAA",
    "invoiceNo": "0000123",
    "invoiceDate": "2026-10-15",
    "sellerTaxCode": "0101234567",
    "amount": "10000000.00",
    "vatAmount": "1050000.00",
    "discountAmount": "0.00",
    "totalAmount": "11050000.00",
    "paymentMethod": "BANK_TRANSFER"
  },
  "lines": [
    {
      "lineNo": 1,
      "description": "Bàn phím",
      "debitAccount": "1561",
      "creditAccount": "331",
      "amount": "10000000.00",
      "materialGoodsCode": "BP01",
      "quantity": 20,
      "unitPrice": "500000.00",
      "vatRate": 0.1,
      "vatAmount": "1050000.00",
      "vatAccount": "1331"
    }
  ],
  "status": "POSTED"
}
```
Lỗi: `404 ERP_VOUCHER_NOT_FOUND`, `502 ERP_UNAVAILABLE`.

### `GET /erp/accounts`
| Query | Mô tả |
|---|---|
| `q` | Số hoặc tên tài khoản |
| `detailOnly` | `true` → chỉ tài khoản chi tiết |

```json
[ { "accountNumber": "1331", "accountName": "Thuế GTGT được khấu trừ của hàng hóa, dịch vụ", "parentNumber": "133", "isDetail": true } ]
```

### `GET /erp/partners?q=&taxCode=&page=&size=`
→ Trang `ErpPartner { code, name, taxCode, address, type }`.

---

## 4. Entry — Hỗ trợ nhập liệu

### `POST /entry/drafts`
Tạo nháp chứng từ từ file hoặc văn bản. Xử lý đồng bộ (timeout 60s).

**Cách 1 — upload file** (`multipart/form-data`):

| Part | Kiểu | Mô tả |
|---|---|---|
| `file` | file | `.xml` (hóa đơn điện tử), `.pdf`, `.png`, `.jpg` — tối đa 10 MB |
| `voucherType` | string | (tùy chọn) gợi ý loại chứng từ |

**Cách 2 — mô tả bằng lời** (`application/json`):
```json
{
  "sourceType": "TEXT",
  "text": "Mua 20 bàn phím của công ty A giá 500.000đ/cái, VAT 10%, chưa thanh toán",
  "voucherDate": "2026-10-15"
}
```

```json
// 201 Created   Location: /api/v1/entry/drafts/5d1e...
{
  "id": "5d1e...",
  "sourceType": "XML",
  "status": "VALIDATED",
  "voucherType": "PURCHASE_INVOICE",
  "data": {
    "voucherDate": "2026-10-15",
    "description": "Mua bàn phím văn phòng",
    "partner": { "code": "NCC001", "name": "Công ty TNHH A", "taxCode": "0101234567", "matched": true },
    "invoice": { "invoiceSeries": "C26TAA", "invoiceNo": "0000123", "amount": "10000000.00", "vatAmount": "1000000.00", "totalAmount": "11000000.00" },
    "lines": [
      { "lineNo": 1, "description": "Bàn phím", "materialGoodsCode": "BP01", "quantity": 20, "unitPrice": "500000.00", "amount": "10000000.00", "vatRate": 0.1, "vatAmount": "1000000.00" }
    ]
  },
  "fieldConfidence": { "partner.code": 1.0, "lines[0].materialGoodsCode": 0.72 },
  "lowConfidenceFields": ["lines[0].materialGoodsCode"],
  "suggestedEntries": [
    { "debitAccount": "1561", "creditAccount": "331", "amount": "10000000.00", "reason": "Mua hàng hóa nhập kho, chưa thanh toán", "source": "RULE_MAPPING" },
    { "debitAccount": "1331", "creditAccount": "331", "amount": "1000000.00", "reason": "Thuế GTGT đầu vào được khấu trừ", "source": "RULE_MAPPING" }
  ],
  "validation": [
    { "ruleId": "XDOC_DUPLICATE_INVOICE", "severity": "ERROR", "message": "Hóa đơn C26TAA-0000123 của MST 0101234567 đã được ghi nhận ở chứng từ MH00098" }
  ],
  "createdAt": "2026-11-16T17:20:00+07:00"
}
```
`suggestedEntries[].source`: `RULE_MAPPING` (suy ra từ danh mục/cấu hình) | `LLM` (do mô hình gợi ý).

Lỗi: `413 FILE_TOO_LARGE`, `415 FILE_TYPE_NOT_SUPPORTED`, `422 ENTRY_EXTRACTION_FAILED`, `502 LLM_UNAVAILABLE`.

### `GET /entry/drafts`
`?status=&page=&size=` → trang các nháp của người dùng hiện tại.

### `GET /entry/drafts/{id}`
→ `200` `EntryDraftResponse` như trên.

### `PUT /entry/drafts/{id}`
Người dùng chỉnh sửa dữ liệu nháp; hệ thống tự chạy lại rule.
```json
{ "data": { ... }, "entries": [ { "debitAccount": "1561", "creditAccount": "331", "amount": "10000000.00" } ] }
```
→ `200` `EntryDraftResponse` (có `validation` mới). Lỗi: `409 ENTRY_DRAFT_NOT_EDITABLE` nếu đã `CONFIRMED`/`DISCARDED`.

### `POST /entry/drafts/{id}/validate`
Chạy lại rule mà không sửa dữ liệu → `200 { "validation": [ ... ] }`.

### `POST /entry/drafts/{id}/confirm`
Xác nhận nháp (người dùng chịu trách nhiệm dữ liệu). Không có `ERROR` chưa xử lý mới cho phép.
```json
{ "acknowledgedWarnings": ["REG_CASH_PAYMENT_THRESHOLD"] }
```
→ `200 { "id": "...", "status": "CONFIRMED", "exportUrl": "/api/v1/entry/drafts/{id}/export" }`
Lỗi: `422 ENTRY_HAS_BLOCKING_ERRORS`.

### `GET /entry/drafts/{id}/export?format=json|xlsx`
Xuất dữ liệu nháp đã xác nhận để nhập vào ERP (file tải về).

### `DELETE /entry/drafts/{id}`
→ `204` (chuyển trạng thái `DISCARDED`).

---

## 5. Rules

### `GET /rules`
`?group=&severity=&entityType=&enabled=&q=` → danh sách (không phân trang):
```json
[
  {
    "ruleId": "DOC_VAT_AMOUNT_MISMATCH",
    "name": "Tiền thuế GTGT không khớp thuế suất",
    "group": "DOC",
    "severity": "ERROR",
    "entityType": "PURCHASE_INVOICE",
    "evaluator": "SPEL",
    "params": { "tolerance": 1 },
    "legalRefs": ["Luật Thuế GTGT"],
    "enabled": true,
    "version": 1,
    "openViolationCount": 14,
    "updatedAt": "2026-11-16T09:00:00+07:00"
  }
]
```

### `GET /rules/{ruleId}`
→ `200` như trên + `definition` (toàn bộ YAML đã parse).

### `PATCH /rules/{ruleId}` — `ADMIN`
Chỉ cho phép sửa các trường cấu hình (không sửa biểu thức).
```json
{ "enabled": true, "severity": "WARNING", "params": { "tolerance": 5 } }
```
→ `200` RuleResponse (version tăng 1). Lỗi: `422 RULE_PARAM_INVALID`.

### `POST /rules/reload` — `ADMIN`
Nạp lại rule từ YAML.
```json
// 200 OK
{ "added": ["ANM_UNIT_PRICE_OUTLIER"], "updated": ["DOC_TOTAL_MISMATCH"], "unchanged": 12, "errors": [] }
```

### `POST /rules/{ruleId}/test` — `ADMIN`
Chạy thử (dry-run) một rule, **không** lưu violation.
```json
// Request: một trong hai
{ "entityType": "PURCHASE_INVOICE", "entityRef": "a1b2c3d4" }
{ "entityType": "PURCHASE_INVOICE", "entity": { ...canonical JSON... } }
```
```json
// 200 OK
{ "ruleId": "DOC_VAT_AMOUNT_MISMATCH", "violated": true, "results": [ { "message": "...", "evidence": { "expected": "1000000.00", "actual": "1050000.00" } } ], "durationMs": 3 }
```

---

## 6. Reviews & Review Jobs

### `POST /reviews/check`
Rà soát tức thời một chứng từ ERP (đồng bộ). Kết quả được lưu thành violation.
```json
{ "entityType": "PURCHASE_INVOICE", "entityRef": "a1b2c3d4", "ruleGroups": ["FLD","DOC","XDOC","REG"] }
```
```json
// 200 OK
{
  "entityRef": "a1b2c3d4",
  "checkedRules": 11,
  "violations": [
    {
      "id": "9a7f...",
      "ruleId": "DOC_VAT_AMOUNT_MISMATCH",
      "severity": "ERROR",
      "status": "OPEN",
      "message": "Dòng 1: tiền thuế 1050000.00 không khớp 10% × 10000000.00 = 1000000.00",
      "evidence": { "lineNo": 1, "expected": "1000000.00", "actual": "1050000.00" }
    }
  ],
  "durationMs": 182
}
```

### `POST /review-jobs` — `CHIEF_ACCOUNTANT`, `ADMIN`
Tạo job rà soát hàng loạt (bất đồng bộ).
```json
{
  "periodFrom": "2026-10-01",
  "periodTo": "2026-10-31",
  "entityTypes": ["PURCHASE_INVOICE", "SALES_INVOICE", "PAYMENT"],
  "ruleGroups": ["FLD", "DOC", "XDOC", "REG", "ANM"],
  "ruleIds": null
}
```
```json
// 202 Accepted   Location: /api/v1/review-jobs/1f2e...
{ "id": "1f2e...", "status": "PENDING", "totalDocuments": 1240, "totalBatches": 7 }
```
Lỗi: `422 REVIEW_PERIOD_TOO_LONG`, `409 REVIEW_JOB_ALREADY_RUNNING` (đã có job đang chạy cùng kỳ).

### `GET /review-jobs`
`?status=&page=&size=` → trang `ReviewJobResponse`.

### `GET /review-jobs/{id}`
```json
{
  "id": "1f2e...",
  "periodFrom": "2026-10-01",
  "periodTo": "2026-10-31",
  "status": "RUNNING",
  "progress": { "totalDocuments": 1240, "processedDocuments": 800, "percent": 64.5, "failedBatches": 0 },
  "violationCount": 37,
  "summary": { "ERROR": 12, "WARNING": 20, "INFO": 5 },
  "requestedBy": { "id": "...", "fullName": "Nguyễn Văn A" },
  "startedAt": "2026-11-16T17:21:00+07:00",
  "finishedAt": null
}
```

### `GET /review-jobs/{id}/events` (SSE)
Stream tiến độ; sự kiện `progress` (payload như `progress` ở trên), `completed`, `failed`.

### `POST /review-jobs/{id}/cancel`
→ `200 { "id": "...", "status": "CANCELLED" }`. Lỗi: `409 REVIEW_JOB_NOT_CANCELLABLE`.

---

## 7. Violations

### `GET /violations`

| Query | Mô tả |
|---|---|
| `status` | Có thể lặp: `status=OPEN&status=EXPLAINED` |
| `severity`, `ruleId`, `group`, `entityType`, `entityRef` | Bộ lọc |
| `reviewJobId` | Violation của một job |
| `entityDateFrom`, `entityDateTo` | Theo ngày chứng từ |
| `page`, `size`, `sort` | `sort` mặc định `severity,asc&sort=detectedAt,desc` |

```json
{
  "items": [
    {
      "id": "9a7f...",
      "ruleId": "DOC_VAT_AMOUNT_MISMATCH",
      "ruleName": "Tiền thuế GTGT không khớp thuế suất",
      "severity": "ERROR",
      "status": "EXPLAINED",
      "entityType": "PURCHASE_INVOICE",
      "entityRef": "a1b2c3d4",
      "entityNo": "MH00123",
      "entityDate": "2026-10-15",
      "message": "Dòng 1: tiền thuế ...",
      "hasExplanation": true,
      "pendingProposalId": null,
      "detectedAt": "2026-11-16T17:21:05+07:00"
    }
  ],
  "page": 0, "size": 20, "totalItems": 37, "totalPages": 2
}
```

### `GET /violations/summary`
`?periodFrom=&periodTo=&reviewJobId=`
```json
{
  "total": 37,
  "bySeverity": { "ERROR": 12, "WARNING": 20, "INFO": 5 },
  "byStatus": { "OPEN": 20, "EXPLAINED": 8, "PROPOSED": 4, "RESOLVED_PENDING_ERP": 3, "DISMISSED": 2 },
  "topRules": [ { "ruleId": "XDOC_DUPLICATE_INVOICE", "count": 9 } ]
}
```

### `GET /violations/{id}`
→ chi tiết violation + `evidence` + `latestExplanation` (nếu có) + danh sách `proposals`.

### `POST /violations/{id}/explain`
Sinh (hoặc lấy từ cache) giải thích và đề xuất.

| Query | Mô tả |
|---|---|
| `regenerate` | `true` → bỏ qua cache |
| `withProposal` | `true` (mặc định) → sinh đề xuất nếu có thể |

Hỗ trợ 2 chế độ theo header `Accept`:
- `application/json` → trả kết quả cuối (đồng bộ).
- `text/event-stream` → stream: `stage` (retrieving / generating / verifying), `token`, `result`, `error`.

```json
// 200 OK (application/json)
{
  "violationId": "9a7f...",
  "explanation": {
    "id": "e4c1...",
    "summary": "Tiền thuế dòng 1 tính sai với thuế suất 10%.",
    "explanation": "Số thuế GTGT bằng giá tính thuế nhân thuế suất [1]. Dòng 1 có giá tính thuế 10.000.000đ, thuế suất 10% nên tiền thuế đúng là 1.000.000đ thay vì 1.050.000đ.",
    "citations": [
      {
        "index": 1,
        "chunkId": "3f2a...",
        "documentId": "d71b...",
        "docNumber": "Luật Thuế GTGT",
        "headingPath": "Chương II > Điều ... > Khoản ...",
        "quote": "…",
        "effectiveFrom": "2025-07-01"
      }
    ],
    "supportStatus": "SUPPORTED",
    "confidence": 0.86,
    "model": "qwen2.5:7b",
    "promptVersion": "explain-v3",
    "cached": false,
    "createdAt": "2026-11-16T17:22:10+07:00"
  },
  "proposal": {
    "id": "b2c9...",
    "status": "PENDING",
    "actionType": "UPDATE_FIELD",
    "changes": [ { "path": "lines[0].vatAmount", "from": "1050000.00", "to": "1000000.00" },
                 { "path": "invoice.totalAmount", "from": "11050000.00", "to": "11000000.00" } ],
    "verification": { "passed": true, "rulesChecked": ["DOC_VAT_AMOUNT_MISMATCH", "DOC_TOTAL_MISMATCH"] }
  },
  "proposalSkippedReason": null
}
```
- Khi không đủ căn cứ: `supportStatus = "UNSUPPORTED"`, `citations = []`, `explanation` chỉ diễn giải từ thông điệp rule kèm câu "Không tìm thấy căn cứ trong kho tài liệu".
- Khi đề xuất không vượt kiểm chứng: `proposal = null`, `proposalSkippedReason = "VERIFICATION_FAILED"`.

Lỗi: `404 VIOLATION_NOT_FOUND`, `502 LLM_UNAVAILABLE`, `504 LLM_TIMEOUT`.

### `POST /violations/{id}/dismiss`
```json
{ "reason": "Chênh lệch do làm tròn theo hóa đơn gốc của người bán" }
```
→ `200` violation với `status = DISMISSED`. Lỗi: `422 DISMISS_REASON_REQUIRED`.

### `POST /violations/{id}/reopen`
→ `200` violation với `status = OPEN`.

---

## 8. Proposals

### `GET /proposals`
`?status=PENDING&entityRef=&violationId=&page=&size=&sort=createdAt,desc`
→ trang `ProposalResponse` (rút gọn).

### `GET /proposals/{id}`
```json
{
  "id": "b2c9...",
  "status": "PENDING",
  "violation": { "id": "9a7f...", "ruleId": "DOC_VAT_AMOUNT_MISMATCH", "severity": "ERROR" },
  "entityType": "PURCHASE_INVOICE",
  "entityRef": "a1b2c3d4",
  "entityNo": "MH00123",
  "actionType": "UPDATE_FIELD",
  "changes": [ { "path": "lines[0].vatAmount", "from": "1050000.00", "to": "1000000.00" } ],
  "before": { ... },
  "after": { ... },
  "rationale": "Tiền thuế phải bằng 10% giá tính thuế.",
  "citations": [ { "index": 1, "docNumber": "...", "headingPath": "..." } ],
  "verification": { "passed": true, "rulesChecked": ["DOC_VAT_AMOUNT_MISMATCH", "DOC_TOTAL_MISMATCH"], "remainingViolations": [] },
  "createdByType": "AGENT",
  "createdAt": "2026-11-16T17:22:11+07:00",
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null
}
```

### `POST /violations/{violationId}/proposals` — `CHIEF_ACCOUNTANT`, `ADMIN`
Người dùng tự tạo đề xuất thủ công (vẫn qua kiểm chứng rule).
```json
{
  "actionType": "UPDATE_FIELD",
  "changes": [ { "path": "lines[0].vatAmount", "to": "1000000.00" } ],
  "rationale": "Điều chỉnh theo hóa đơn gốc"
}
```
→ `201` ProposalResponse. Lỗi: `422 PROPOSAL_VERIFICATION_FAILED` (kèm `remainingViolations`), `422 PROPOSAL_PATH_INVALID`.

### `POST /proposals/{id}/approve` — `CHIEF_ACCOUNTANT`, `ADMIN`
```json
{ "note": "Đồng ý, chuyển bộ phận nhập liệu điều chỉnh" }
```
→ `200` ProposalResponse (`status = APPROVED`). Lỗi: `409 PROPOSAL_NOT_PENDING`, `403 PROPOSAL_SELF_APPROVAL` (người tạo thủ công không tự duyệt).

### `POST /proposals/{id}/reject` — `CHIEF_ACCOUNTANT`, `ADMIN`
```json
{ "note": "Hóa đơn gốc người bán ghi đúng 1.050.000đ, cần liên hệ người bán" }
```
→ `200` (`status = REJECTED`). Lỗi: `422 REJECT_NOTE_REQUIRED`, `409 PROPOSAL_NOT_PENDING`.

### `POST /proposals/export` — `CHIEF_ACCOUNTANT`, `ADMIN`
Xuất phiếu đề nghị điều chỉnh cho các đề xuất đã duyệt.
```json
{ "proposalIds": ["b2c9...", "c3d0..."], "format": "PDF" }
```
→ `200` file (`application/pdf` hoặc `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`); các đề xuất chuyển `EXPORTED`.

---

## 9. Knowledge

### `POST /knowledge/documents` — `ADMIN`
`multipart/form-data`:

| Part | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `file` | file | Có | PDF / DOCX / HTML / MD, ≤ 20 MB |
| `metadata` | JSON | Có | Xem dưới |

```json
{
  "title": "Thông tư hướng dẫn chế độ kế toán doanh nghiệp",
  "docNumber": "XX/YYYY/TT-BTC",
  "docType": "CIRCULAR",
  "issuedDate": "2025-01-01",
  "effectiveFrom": "2025-01-01",
  "effectiveTo": null,
  "supersedesDocumentId": null,
  "sourceUrl": "https://..."
}
```
→ `202 Accepted { "id": "d71b...", "status": "PENDING" }` (ingestion chạy nền).
Lỗi: `409 KNOWLEDGE_DOCUMENT_DUPLICATE` (trùng sha256), `415 FILE_TYPE_NOT_SUPPORTED`.

### `GET /knowledge/documents`
`?docType=&status=&effectiveAt=2026-10-15&q=&page=&size=`
```json
{
  "items": [
    {
      "id": "d71b...",
      "title": "...",
      "docNumber": "XX/YYYY/TT-BTC",
      "docType": "CIRCULAR",
      "effectiveFrom": "2025-01-01",
      "effectiveTo": null,
      "status": "READY",
      "chunkCount": 412,
      "embeddingModel": "bge-m3",
      "createdAt": "2026-11-10T10:00:00+07:00"
    }
  ],
  "page": 0, "size": 20, "totalItems": 8, "totalPages": 1
}
```

### `GET /knowledge/documents/{id}`
→ chi tiết + `errorMessage` (nếu `FAILED`).

### `GET /knowledge/documents/{id}/chunks?page=&size=`
→ trang `{ id, chunkIndex, headingPath, content, tokenCount }` (để kiểm tra chất lượng chunking).

### `PATCH /knowledge/documents/{id}` — `ADMIN`
Sửa metadata hiệu lực (không re-embed).
```json
{ "effectiveTo": "2025-12-31", "supersededById": "e82c..." }
```

### `POST /knowledge/documents/{id}/reindex` — `ADMIN`
→ `202` (xóa chunk cũ, chạy lại ingestion).

### `DELETE /knowledge/documents/{id}` — `ADMIN`
→ `204` (chuyển `ARCHIVED`, xóa chunk).

### `POST /knowledge/search`
Truy xuất trực tiếp (dùng cho debug và đánh giá RAG).
```json
{ "query": "điều kiện khấu trừ thuế GTGT đầu vào", "asOf": "2026-10-15", "topK": 5, "docTypes": ["LAW","DECREE","CIRCULAR"], "rerank": true }
```
```json
{
  "results": [
    {
      "chunkId": "3f2a...",
      "documentId": "d71b...",
      "docNumber": "…",
      "title": "…",
      "headingPath": "Chương II > Điều … > Khoản …",
      "content": "…",
      "vectorRank": 2,
      "textRank": 1,
      "rrfScore": 0.0325,
      "rerankScore": 0.91
    }
  ],
  "timings": { "embeddingMs": 45, "searchMs": 30, "rerankMs": 210 }
}
```

---

## 10. Assistant (chat)

### `POST /assistant/sessions`
```json
{ "channel": "CHAT", "title": null }
```
→ `201 { "id": "s-41aa...", "channel": "CHAT", "title": null, "createdAt": "..." }`

### `GET /assistant/sessions?page=&size=`
→ trang phiên của người dùng hiện tại.

### `GET /assistant/sessions/{id}/messages?page=&size=`
```json
{
  "items": [
    { "id": "...", "seq": 1, "role": "USER", "content": "Tháng 10 có bao nhiêu hóa đơn mua vào chưa thanh toán?", "createdAt": "..." },
    { "id": "...", "seq": 2, "role": "TOOL", "toolName": "query_erp_vouchers", "toolArgs": { "periodFrom": "2026-10-01", "periodTo": "2026-10-31", "voucherType": "PURCHASE_INVOICE", "filters": { "unpaid": true } } },
    { "id": "...", "seq": 3, "role": "ASSISTANT", "content": "Tháng 10/2026 có 18 hóa đơn mua vào chưa thanh toán, tổng 245.300.000đ…", "citations": [] }
  ],
  "page": 0, "size": 50, "totalItems": 3, "totalPages": 1
}
```

### `POST /assistant/sessions/{id}/messages`
Gửi câu hỏi; trả lời dạng stream.

```http
POST /api/v1/assistant/sessions/s-41aa.../messages
Accept: text/event-stream
Content-Type: application/json

{ "content": "Vì sao chứng từ MH00123 bị cảnh báo?", "context": { "entityRef": "a1b2c3d4" } }
```

Luồng sự kiện:
```text
event: message_start
data: {"messageId":"m-77..","sessionId":"s-41aa.."}

event: tool_call
data: {"tool":"get_erp_voucher","args":{"voucherRef":"a1b2c3d4"}}

event: tool_result
data: {"tool":"get_erp_voucher","status":"OK","summary":"Chứng từ MH00123, 1 dòng"}

event: tool_call
data: {"tool":"run_rules","args":{"entityType":"PURCHASE_INVOICE","entityRef":"a1b2c3d4"}}

event: token
data: {"text":"Chứng từ MH00123 "}

event: token
data: {"text":"bị cảnh báo vì tiền thuế…"}

event: citations
data: {"citations":[{"index":1,"docNumber":"…","headingPath":"…","chunkId":"3f2a.."}]}

event: done
data: {"messageId":"m-77..","tokensIn":1834,"tokensOut":212,"latencyMs":9120,"steps":2}
```
Sự kiện lỗi: `event: error` / `data: {"code":"LLM_TIMEOUT","message":"..."}`.

> `EventSource` của trình duyệt chỉ hỗ trợ `GET`, nên frontend dùng `fetch()` + `ReadableStream` để đọc SSE từ `POST`.

Ràng buộc: `content` 1–4000 ký tự; mỗi phiên tối đa 1 request đang xử lý (`409 ASSISTANT_SESSION_BUSY`).

### `DELETE /assistant/sessions/{id}`
→ `204`.

---

## 11. Feedback, Audit, Dashboard

### `POST /feedback`
```json
{ "targetType": "EXPLANATION", "targetId": "e4c1...", "rating": 1, "comment": "Giải thích rõ, trích dẫn đúng" }
```
→ `201`. Gửi lại cùng `targetType + targetId` sẽ ghi đè (`200`).

### `GET /audit-logs` — `CHIEF_ACCOUNTANT`, `ADMIN`
`?actorType=&actorId=&action=&targetType=&targetId=&from=&to=&page=&size=`
```json
{
  "items": [
    {
      "id": 10293,
      "occurredAt": "2026-11-16T17:25:00+07:00",
      "actorType": "USER",
      "actor": { "id": "...", "username": "chief" },
      "action": "PROPOSAL_APPROVED",
      "targetType": "PROPOSAL",
      "targetId": "b2c9...",
      "detail": { "note": "Đồng ý…" },
      "traceId": "4bf92f3577b34da6"
    }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

### `GET /dashboard/stats`
`?periodFrom=&periodTo=`
```json
{
  "violations": { "total": 37, "open": 20, "resolved": 10, "bySeverity": { "ERROR": 12, "WARNING": 20, "INFO": 5 } },
  "topRules": [ { "ruleId": "XDOC_DUPLICATE_INVOICE", "name": "Hóa đơn đầu vào bị nhập trùng", "count": 9 } ],
  "proposals": { "pending": 4, "approved": 10, "rejected": 2, "acceptanceRate": 0.83 },
  "ai": { "explanations": 52, "supportedRate": 0.88, "avgLatencyMs": 8700, "positiveFeedbackRate": 0.79 },
  "entry": { "drafts": 64, "confirmed": 51, "avgLowConfidenceFields": 1.3 }
}
```

### Hệ thống
| Endpoint | Mô tả |
|---|---|
| `GET /actuator/health` | Trạng thái `appDb`, `erpDb`, `ollama`, `reranker`, `rabbit` |
| `GET /actuator/prometheus` | Metrics (chỉ mạng nội bộ) |

---

## 12. Ma trận phân quyền

| Endpoint | ACCOUNTANT | CHIEF_ACCOUNTANT | ADMIN |
|---|:-:|:-:|:-:|
| `/erp/**` (GET) | ✅ | ✅ | ✅ |
| `/entry/drafts/**` | ✅ (của mình) | ✅ | ✅ |
| `GET /rules/**` | ✅ | ✅ | ✅ |
| `PATCH /rules/**`, `/rules/reload`, `/rules/*/test` | ❌ | ❌ | ✅ |
| `POST /reviews/check` | ✅ | ✅ | ✅ |
| `/review-jobs` (POST, cancel) | ❌ | ✅ | ✅ |
| `/review-jobs` (GET) | ✅ | ✅ | ✅ |
| `/violations` (GET, explain) | ✅ | ✅ | ✅ |
| `/violations/*/dismiss`, `/reopen` | ✅ | ✅ | ✅ |
| `GET /proposals/**` | ✅ | ✅ | ✅ |
| Tạo đề xuất thủ công, approve, reject, export | ❌ | ✅ | ✅ |
| `/knowledge/search`, `GET /knowledge/**` | ✅ | ✅ | ✅ |
| `/knowledge/**` (ghi) | ❌ | ❌ | ✅ |
| `/assistant/**` | ✅ | ✅ | ✅ |
| `/audit-logs` | ❌ | ✅ | ✅ |
| `/dashboard/stats` | ✅ | ✅ | ✅ |

Tool `create_proposal` trong chat chỉ được cấp cho phiên của `CHIEF_ACCOUNTANT` và `ADMIN`.

---

## 13. Danh mục enum

| Enum | Giá trị |
|---|---|
| `Role` | `ACCOUNTANT`, `CHIEF_ACCOUNTANT`, `ADMIN` |
| `VoucherType` / `EntityType` | `PURCHASE_INVOICE`, `SALES_INVOICE`, `PAYMENT`, `RECEIPT`, `JOURNAL_ENTRY`, `STOCK_IN`, `STOCK_OUT` |
| `RuleGroup` | `FLD`, `DOC`, `XDOC`, `REG`, `ANM` |
| `Severity` | `ERROR`, `WARNING`, `INFO` |
| `ViolationStatus` | `OPEN`, `EXPLAINED`, `PROPOSED`, `RESOLVED_PENDING_ERP`, `RESOLVED`, `DISMISSED` |
| `ReviewJobStatus` | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `ProposalStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUPERSEDED`, `EXPORTED` |
| `ProposalActionType` | `UPDATE_FIELD`, `ADJUSTMENT_ENTRY`, `MARK_DUPLICATE`, `CANCEL_VOUCHER` |
| `EntrySourceType` | `XML`, `IMAGE`, `PDF`, `TEXT` |
| `EntryDraftStatus` | `DRAFT`, `VALIDATED`, `CONFIRMED`, `DISCARDED` |
| `DocType` | `LAW`, `DECREE`, `CIRCULAR`, `MANUAL`, `INTERNAL` |
| `KnowledgeDocumentStatus` | `PENDING`, `INGESTING`, `READY`, `FAILED`, `ARCHIVED` |
| `SupportStatus` | `SUPPORTED`, `PARTIAL`, `UNSUPPORTED` |
| `PaymentMethod` | `CASH`, `BANK_TRANSFER`, `CARD`, `OTHER`, `UNKNOWN` |

---

## 14. Danh mục mã lỗi

| Mã | HTTP | Mô tả |
|---|---|---|
| `AUTH_INVALID_CREDENTIALS` | 401 | Sai tên đăng nhập / mật khẩu |
| `AUTH_TOKEN_EXPIRED` | 401 | Access token hết hạn |
| `AUTH_USER_DISABLED` | 403 | Tài khoản bị khóa |
| `ACCESS_DENIED` | 403 | Không đủ quyền |
| `COMPANY_ACCESS_DENIED` | 403 | Truy cập dữ liệu công ty khác |
| `VALIDATION_FAILED` | 422 | Lỗi validate chung (xem `errors[]`) |
| `ERP_VOUCHER_NOT_FOUND` | 404 | Không tìm thấy chứng từ ERP |
| `ERP_UNAVAILABLE` | 502 | Không kết nối được DB ERP |
| `ERP_QUERY_TIMEOUT` | 504 | Truy vấn ERP quá thời gian |
| `FILE_TOO_LARGE` | 413 | File vượt giới hạn |
| `FILE_TYPE_NOT_SUPPORTED` | 415 | Định dạng không hỗ trợ |
| `ENTRY_EXTRACTION_FAILED` | 422 | Không trích xuất được dữ liệu chứng từ |
| `ENTRY_DRAFT_NOT_EDITABLE` | 409 | Nháp đã xác nhận / hủy |
| `ENTRY_HAS_BLOCKING_ERRORS` | 422 | Còn lỗi `ERROR` chưa xử lý |
| `RULE_NOT_FOUND` | 404 | Không có rule |
| `RULE_PARAM_INVALID` | 422 | Tham số rule không hợp lệ |
| `RULE_RELOAD_FAILED` | 422 | YAML sai cú pháp / biểu thức lỗi |
| `REVIEW_PERIOD_TOO_LONG` | 422 | Kỳ rà soát > 12 tháng |
| `REVIEW_JOB_ALREADY_RUNNING` | 409 | Đã có job cùng kỳ đang chạy |
| `REVIEW_JOB_NOT_CANCELLABLE` | 409 | Job đã kết thúc |
| `VIOLATION_NOT_FOUND` | 404 | Không có violation |
| `DISMISS_REASON_REQUIRED` | 422 | Thiếu lý do bỏ qua |
| `PROPOSAL_NOT_FOUND` | 404 | Không có đề xuất |
| `PROPOSAL_NOT_PENDING` | 409 | Đề xuất không ở trạng thái chờ duyệt |
| `PROPOSAL_VERIFICATION_FAILED` | 422 | Đề xuất không vượt kiểm chứng rule |
| `PROPOSAL_PATH_INVALID` | 422 | Đường dẫn trường thay đổi không hợp lệ |
| `PROPOSAL_SELF_APPROVAL` | 403 | Không được tự duyệt đề xuất của mình |
| `REJECT_NOTE_REQUIRED` | 422 | Thiếu lý do từ chối |
| `KNOWLEDGE_DOCUMENT_DUPLICATE` | 409 | Tài liệu đã tồn tại |
| `KNOWLEDGE_INGESTION_FAILED` | 422 | Lỗi xử lý tài liệu |
| `ASSISTANT_SESSION_BUSY` | 409 | Phiên đang xử lý câu hỏi khác |
| `LLM_UNAVAILABLE` | 502 | Không gọi được LLM |
| `LLM_TIMEOUT` | 504 | LLM phản hồi quá thời gian |
| `LLM_OUTPUT_INVALID` | 502 | Output LLM không đúng schema sau khi retry |
| `RERANKER_UNAVAILABLE` | 502 | Reranker lỗi (hệ thống fallback dùng thứ hạng RRF) |

---

## Phụ lục: Trạng thái triển khai

| Nhóm | Endpoint | Trạng thái | Ghi chú |
|---|---|---|---|
| Auth | `/auth/login`, `/auth/refresh`, `/auth/me` | ✅ | |
| ERP | `GET /erp/vouchers`, `/erp/vouchers/{ref}`, `/erp/accounts` | ✅ một phần | `entityType` = `PURCHASE_INVOICE` (mặc định), `SALES_INVOICE`, `PAYMENT` (khóa bán hàng dạng `GUID@yyyy-MM-dd`); hỗ trợ thêm `RECEIPT`, `BANK_DEBIT`, `BANK_CREDIT`, `PURCHASE_RETURN`, `SALES_RETURN`; có `q`, `partnerTaxCode`, `hasViolations`, `openViolationCount`, `maxSeverity` (với `hasViolations=true` khóa lấy từ bảng violation, `q`/`partnerTaxCode` lọc trên trang kết quả); `vatRate` trả nguyên giá trị ERP (đơn vị chưa xác nhận). Lỗi hạ tầng: `502 ERP_UNAVAILABLE`, `504 ERP_QUERY_TIMEOUT`, `404 ERP_VOUCHER_NOT_FOUND` |
| Rules | `GET /rules`, `/rules/{id}`, `PATCH /rules/{id}`, `POST /rules/reload`, `POST /rules/{id}/test` | ✅ | `test` chạy thử không lưu violation (kể cả rule đang tắt) |
| Reviews | `POST /reviews/check` | ✅ | `PURCHASE_INVOICE`, `SALES_INVOICE`, `PAYMENT`; evaluator SPEL và SQL |
| Review jobs | `POST/GET /review-jobs`, `/{id}`, `/{id}/cancel` | ✅ một phần | RabbitMQ, batch 200, retry 3 lần → DLQ. `GET /review-jobs/{id}/events` (SSE: `progress`, `completed`, `failed`). Nhiều `entityTypes` trong một job (mỗi loại có batch riêng) |
| Violations | `GET /violations`, `/summary`, `/{id}`, `/dismiss`, `/reopen` | ✅ | `sort=severity` theo thứ hạng ERROR→WARNING→INFO; `hasExplanation`/`pendingProposalId` chưa có dữ liệu |
| Violations | `POST /violations/{id}/explain` | ✅ | JSON (mặc định) hoặc SSE (`Accept: text/event-stream` / `?sse=1`: `stage` → `result`/`error`), `regenerate`, `withProposal`. Citation do LLM trả theo `index` đoạn, backend ánh xạ sang `chunkId`. Chưa thử với Ollama thật |
| Proposals | `GET /proposals`, `/{id}`, `POST /violations/{id}/proposals`, `/approve`, `/reject`, `/export` | ✅ một phần | Chỉ `UPDATE_FIELD`; export **CSV, XLSX hoặc PDF** (`format`). Kiểm chứng bằng rule bắt buộc |
| Knowledge | `POST/GET /knowledge/documents`, `/{id}`, `/chunks`, `/reindex`, `POST /knowledge/search` | ✅ một phần | pdf/docx/txt/md; `PATCH` metadata và `DELETE` (lưu trữ); `search` trả `RetrievalResult` đơn giản hơn đặc tả |
| Feedback / Audit / Dashboard | `POST /feedback`, `GET /audit-logs`, `GET /dashboard/stats` | ✅ | Thống kê `entry` luôn 0 cho tới khi có module Entry |
| Entry | `POST/GET /entry/drafts`, `/{id}`, `PUT /{id}`, `/validate`, `/confirm`, `/export`, `DELETE /{id}` | ✅ một phần | XML (TT78), PDF, ảnh, văn bản (LLM chỉ chạy với `AI_PROVIDER=ollama`). Định khoản gợi ý là mặc định TT133 (tin cậy 0.5). `export?format=json|xlsx`. Nguồn nhập: XML (tất định), PDF/ảnh/văn bản (LLM local, có `lowConfidenceFields`); `POST /entry/drafts` nhận multipart `file` hoặc JSON `{sourceText}`. Kế toán viên chỉ thấy nháp của mình |
| Assistant (chat, SSE) | `POST/GET /assistant/sessions`, `GET /{id}/messages`, `POST /{id}/messages` (SSE), `DELETE /{id}` | ✅ một phần | Vòng lặp công cụ tường minh (`max-steps`), 7 công cụ chỉ-đọc/đề xuất, lọc theo vai trò. Sự kiện: `message_start`, `tool_call`, `tool_result`, `token`, `citations`, `done`, `error` (+`ping` nội bộ). Chưa thử với Ollama thật; khi `AI_PROVIDER` ≠ ollama câu trả lời không stream từng token (đợi khôi phục token đã mask). Công cụ đọc ERP mới hỗ trợ hóa đơn mua hàng |
