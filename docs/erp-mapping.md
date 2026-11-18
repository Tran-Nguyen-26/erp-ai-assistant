# Ánh xạ ERP (SQL Server) → canonical model

> Nguồn: `docs/erp-schema/columns.json` (chỉ lưu cục bộ, không commit — xem `.gitignore`) (khảo sát metadata, 501 bảng, schema `dbo`, 2026-11-17).
> Trạng thái: **DRAFT** — các mục "Cần xác nhận" chưa được kiểm chứng bằng dữ liệu.
> Tên cột ERP chỉ được xuất hiện trong `erp/adapter` và `resources/sql/erp/`.

## 1. Đặc điểm DB (ảnh hưởng thiết kế truy vấn)

| Đặc điểm | Hệ quả |
|---|---|
| Đa công ty: `CompanyID` (uniqueidentifier) ở 192/501 bảng | Mọi query có `CompanyID = :companyId`; `companyId` của app là chuỗi GUID |
| Bảng rất lớn: `GeneralLedgerDetail` ~190 triệu, `GeneralLedger` ~66 triệu, `SAInvoiceDetail` ~27 triệu | Bắt buộc lọc kỳ (`PostedDate`/`Date`) + phân trang `OFFSET…FETCH`; cấm quét toàn bảng; timeout truy vấn |
| Khóa là GUID | `entityRef` = GUID dạng chuỗi |
| Phiên bản số tiền: cột `X` (quy đổi VND) và `XOriginal` (nguyên tệ) | Canonical `amount` = cột VND, `amountOriginal` = nguyên tệ |
| Hai sổ: `TypeLedger`, `NoFBook`/`NoMBook` (sổ tài chính / sổ quản trị) | **Cần xác nhận**: rule chỉ chạy trên sổ tài chính |
| Bảng `*_bak`, `*BackUp`, `*_Staging`, `*_Old` | Bỏ qua, không dùng |

## 2. Bảng nguồn theo loại chứng từ

| Canonical `entityType` | Header | Chi tiết | Ghi chú |
|---|---|---|---|
| `PURCHASE_INVOICE` (mua hàng) | `PPInvoice` (2,1M) | `PPInvoiceDetail` (6M) | Hóa đơn đầu vào còn ở `PPInvoiceDetail.InvoiceNo/Series/Date` và `PPBill` |
| `SALES_INVOICE` (bán hàng) | `SAInvoice` (12M) | `SAInvoiceDetail` (27M) | Hóa đơn điện tử: `SABill`, `SABillDetail`, `SABillEI` |
| `PAYMENT` (chi tiền mặt) | `MCPayment` | `MCPaymentDetail` | |
| `RECEIPT` (thu tiền mặt) | `MCReceipt` | `MCReceiptDetail` | |
| Ngân hàng | `MBDeposit`, `MBTellerPaper`, `MBCreditCard` | `…Detail` | |
| `JOURNAL_ENTRY` (khác) | `GOtherVoucher` | `GOtherVoucherDetail` | |
| Kho | `RSInwardOutward` | `RSInwardOutwardDetail` | Tồn kho: `RepositoryLedger` (28M) |

Sổ cái hợp nhất mọi chứng từ: `GeneralLedger` (header dòng sổ, 58 cột) ↔ `GeneralLedgerDetail`
(Account / AccountCorresponding / DebitAmount / CreditAmount) nối qua `DetailID`/`ReferenceID`.
`ReferenceID` = ID chứng từ gốc, `TypeID` = loại chứng từ.

## 3. Ánh xạ trường

### ErpVoucher ← header (ví dụ PPInvoice)
| Canonical | Cột ERP | Ghi chú |
|---|---|---|
| `ref` | `ID` | GUID |
| `companyId` | `CompanyID` | |
| `voucherType` | `TypeID` | Cần bảng `Type` (xem §5) |
| `voucherNo` | `NoFBook` | **Cần xác nhận** (so với `NoMBook`) |
| `voucherDate` | `Date` | |
| `postedDate` | `PostedDate` | |
| `description` | `Reason` | Nội dung không tin cậy → bọc delimiter trong prompt |
| `partner` | `AccountingObjectID` + `AccountingObjectName`, `CompanyTaxCode` (MST **bên mua** hay bán? — cần xác nhận) | PII: mask khi dùng LLM cloud |
| `currency` / `exchangeRate` | `CurrencyID` / `ExchangeRate` | |
| `totalAmount` | `TotalAmount` | VND |
| `status` | `Recorded` (bool, đã ghi sổ) | **Cần xác nhận** |

### ErpJournalLine ← detail
| Canonical | Cột ERP |
|---|---|
| `debitAccount` / `creditAccount` | `DebitAccount` / `CreditAccount` |
| `amount` / `amountOriginal` | `Amount` / `AmountOriginal` |
| `materialGoodsCode` | `MaterialGoodsID` → `MaterialGoods` |
| `quantity` / `unitPrice` | `Quantity` / `UnitPrice` |
| `vatRate` / `vatAmount` / `vatAccount` | `VATRate` / `VATAmount` / `VATAccount` |

### ErpInvoice
| Canonical | Cột ERP |
|---|---|
| `invoiceTemplate` / `invoiceSeries` / `invoiceNo` / `invoiceDate` | `InvoiceTemplate` / `InvoiceSeries` / `InvoiceNo` / `InvoiceDate` (ở detail với PP; ở header với SA) |
| `totalAmount` / `vatAmount` / `discountAmount` | `TotalAmount` / `TotalVATAmount` / `TotalDiscountAmount` |
| `paymentMethod` | `SAInvoice.PaymentMethod` (mua hàng: **cần xác nhận**, có thể suy từ `PaymentVoucherID`) |

### ErpAccount ← `AccountList`
`accountNumber`=`AccountNumber`, `accountName`=`AccountName`, `parentNumber`← `ParentAccountID`,
`isDetail` = `NOT IsParentNode`, `accountingRegime`← `AccountingType` (**cần xác nhận**: giá trị nào = TT200/TT133).

### ErpPartner ← `AccountingObject`
`code`=`AccountingObjectCode`, `name`=`AccountingObjectName`, `taxCode`=`TaxCode`, `address`=`AccountingObjectAddress`.

## 4. Chi phí & rủi ro truy vấn
- Không có thông tin index trong file khảo sát → **cần** xem index của `CompanyID`, `PostedDate`/`Date` trên các bảng lớn.
- Rule `XDOC_DUPLICATE_INVOICE`: tìm trùng (MST bán, ký hiệu, số HĐ) — phải giới hạn công ty + khoảng ngày, không quét toàn bảng.

## 5. Loại chứng từ (`TypeID`, từ `docs/erp-schema/type.json`)

| Nhóm | TypeID | Bảng header |
|---|---|---|
| Mua hàng / mua dịch vụ | 210–215 (hàng), 240–245 (dịch vụ) | `PPInvoice` |
| Hàng mua trả lại / giảm giá | 220–222, 230–232 | `PPInvoice` |
| Hóa đơn đầu vào (kê khai) | 250–255 | `PPBill` |
| Bán hàng | 320–323 | `SAInvoice` |
| Hàng bán trả lại / giảm giá | 330–335, 340–345 | `SAInvoice` |
| Xuất hóa đơn | 350–353 | `SABill` |
| Phiếu thu / chi | 100–104 / 110–118 | `MCReceipt` / `MCPayment` |
| Báo Nợ / Báo Có | 120–145 / 160–164 | `MBTellerPaper` / `MBDeposit` |
| Nhập / xuất kho | 400–405 / 410–415 | `RSInwardOutward` |
| Chứng từ nghiệp vụ khác | 700–711, 7010–7012 | `GOtherVoucher` |

## 6. Index và chiến lược truy vấn (từ `docs/erp-schema/indexes.json`)

| Bảng | Index hữu ích | Chiến lược |
|---|---|---|
| `GeneralLedger` | `(CompanyID, PostedDate, TypeLedger, TypeID, …)`; phân vùng theo `PostedDate` | Luôn lọc `CompanyID` + khoảng `PostedDate` |
| `GeneralLedgerDetail` | `(CompanyID, PostedDate)`, `(CompanyID, Account, AccountCorresponding, PostedDate)`, `(PostedDate, ReferenceID)` | Luôn kèm `PostedDate` |
| `PPInvoice` | **chỉ** `CompanyID` (+ `CompanyID, AccountingObjectID, TypeLedger`, …); **không có index ngày** | Lọc theo `CompanyID`; giới hạn kỳ trên `Date` sẽ quét theo công ty → dùng phân trang, cân nhắc lấy danh sách ID từ `GeneralLedger` |
| `PPInvoiceDetail` | `PPInvoiceID` | Nối theo `PPInvoiceID` |
| `SAInvoice` | `(Date, CompanyID, …)`, PK `(Date, ID)` | Lọc `Date` + `CompanyID`; tra theo ID cần thêm `Date` |
| `SAInvoiceDetail` | PK `(DatePartition, ID)`, `(DatePartition, SAInvoiceID)` | Luôn kèm `DatePartition` |
| `AccountList` | `(AccountNumber, CompanyID)` unique | |

## 7. Chế độ kế toán
`AccountList.AccountingType = 1` cho toàn bộ 1,69 triệu dòng; tên DB `EB133_GR1` → giả định **TT133** (cần xác nhận với người dùng).

## 8. Còn cần xác nhận
1. Chọn một `CompanyID` doanh nghiệp để demo.
2. `NoFBook` hay `NoMBook` là số chứng từ hiển thị; ý nghĩa `TypeLedger` (0/1/2).
3. `PPInvoice.CompanyTaxCode` là MST của bên nào.
4. **`VATRate`**: lưu dạng phần trăm (`10`) hay phân số (`0.1`)? Giá trị đặc biệt cho KCT/KKKNT (ví dụ `-1`)? Rule `DOC_VAT_AMOUNT_MISMATCH` đang **tắt** cho tới khi xác nhận (tham số `rateDivisor`).
5. **`TotalAmount`** của `PPInvoice` đã gồm VAT chưa (cần cho rule `DOC_TOTAL_MISMATCH` và `ErpInvoice.amount`; hiện adapter suy ra `amount = total − VAT + giảm giá`, chưa kiểm chứng).

## 9. Truy vấn rule liên chứng từ & tra cứu NCC (đã thêm)
- `rule-duplicate-invoice.sql`: trùng (MST bán, ký hiệu, số HĐ) — nối `PPInvoice → PPInvoiceDetail → AccountingObject`. `PPInvoiceDetail` **không có index** theo số hóa đơn nên truy vấn dựa vào index `CompanyID` của `PPInvoice` + `TOP 5`; cần đo trên dữ liệu thật và có thể cần giới hạn khoảng ngày.
- `partner-by-tax-code.sql`: tra `AccountingObject` theo `CompanyID + TaxCode`; chưa biết index của `TaxCode`.

## 10. Hóa đơn bán hàng và phiếu chi (đã thêm)
| Canonical | Header | Chi tiết | Ghi chú |
|---|---|---|---|
| `SALES_INVOICE` | `SAInvoice` | `SAInvoiceDetail` | TypeID 320–322. **Khóa `entityRef` dạng `GUID@yyyy-MM-dd`**: PK của `SAInvoice` là `(Date, ID)` và dòng nối qua `DatePartition`, nên có ngày thì truy vấn dùng index; không có ngày thì chỉ lọc `CompanyID + ID` (chậm). Số/ký hiệu hóa đơn nằm ở **header** |
| `PAYMENT` | `MCPayment` | `MCPaymentDetail` | TypeID 110–118 (phiếu chi tiền mặt). Chưa gồm báo Nợ (`MBTellerPaper`) |

Cần xác nhận khi chạy với dữ liệu thật: (1) `DatePartition` có bằng `Date` của header (đã có truy vấn dự phòng không lọc phân vùng); (2) TypeID bán hàng khác (330–353 hàng bán trả lại/giảm giá/xuất hóa đơn) nằm ở bảng nào; (3) ý nghĩa `SAInvoice.CompanyTaxCode` (hiện dùng MST từ `AccountingObject` làm MST đối tác).
Rule áp dụng cho cả ba loại: `FLD_*`, `DOC_ACCOUNT_NOT_DETAIL`, `REG_ACCOUNT_REGIME_MISMATCH`; rule mua hàng (`DOC_VAT_*`, `XDOC_DUPLICATE_INVOICE`, `ANM_UNIT_PRICE_OUTLIER`, `REG_CASH_*`) chỉ chạy trên `PURCHASE_INVOICE`.

## 11. Phiếu thu, báo Nợ/Có, hàng trả lại (đã thêm)

Sáu loại chứng từ dùng chung `ErpVoucherKindAdapter` và cùng bộ alias cột (header/lines), xem `sql/erp/<loai>-*.sql`:

| Loại | Bảng | TypeID | Phương thức TT |
|---|---|---|---|
| PAYMENT | MCPayment | 110–118 | CASH |
| RECEIPT | MCReceipt | 100–104 | CASH |
| BANK_DEBIT | MBTellerPaper | 120–128, 130–135, 140–145 | 14x → CASH, còn lại BANK_TRANSFER |
| BANK_CREDIT | MBDeposit | 160–164 | BANK_TRANSFER |
| PURCHASE_RETURN | PPDiscountReturn | 220–222, 230–232 | theo cột |
| SALES_RETURN | SAReturn | 330–335 | theo cột |

**Chưa xác minh với dữ liệu thật** (SQL được rà thủ công, không chạy được trên SQL Server, không có công cụ kiểm cú pháp T-SQL): tên cột ở các bảng trên, cột hóa đơn ở header/dòng, TypeID 340–345 (giảm giá bán hàng) nằm ở bảng nào, ngữ nghĩa `TotalAmount` (đã gồm thuế hay không — quyết định rule `DOC_TOTAL_MISMATCH`), đơn vị `VATRate` (rule `DOC_INVALID_VAT_RATE`), số dư công nợ 331 (rule `XDOC_PAYMENT_EXCEEDS_DEBT`).
