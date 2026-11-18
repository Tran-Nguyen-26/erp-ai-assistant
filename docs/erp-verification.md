# Kiểm tra giả định về dữ liệu ERP (chạy trên DataGrip)

Mọi câu dưới đây **chỉ `SELECT`**, đã giới hạn `TOP`/khoảng ngày để nhẹ. Thay `@company` bằng `CompanyID` của bạn
(`DECLARE @company UNIQUEIDENTIFIER = '...'`). Dán kết quả (đã che tên/MST nếu cần; **không gửi dữ liệu thật nhạy cảm**)
để bật các rule đang tắt. Mỗi mục ghi rõ "kết quả mong đợi" và "nếu khác thì sao".

```sql
DECLARE @company UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000000';  -- sửa
DECLARE @from DATE = '2026-01-01', @to DATE = '2026-12-31';
```

## 1. Đơn vị thuế suất `VATRate` (rule `DOC_VAT_AMOUNT_MISMATCH`, `DOC_INVALID_VAT_RATE`)
```sql
SELECT TOP 20 d.VATRate, COUNT(*) AS n
FROM dbo.PPInvoiceDetail d JOIN dbo.PPInvoice h ON h.ID = d.PPInvoiceID
WHERE h.CompanyID = @company AND h.[Date] >= @from AND h.[Date] < DATEADD(DAY,1,@to)
GROUP BY d.VATRate ORDER BY n DESC;
```
- Mong đợi: các giá trị như `10`, `8`, `5`, `0` (đơn vị %) **hoặc** `0.1`, `0.08` (phân số); có thể có `-1` (không chịu thuế).
- Nếu là %: `rateDivisor: 100` (rule VAT) / `1` (rule thuế suất hợp lệ). Nếu là phân số: `1` / `0.01`.

## 2. `VATAmount` có đúng `Amount × VATRate` không
```sql
SELECT TOP 20 d.Amount, d.VATRate, d.VATAmount,
       ROUND(d.Amount * d.VATRate / 100.0, 0) AS expected_if_percent,
       ROUND(d.Amount * d.VATRate, 0)         AS expected_if_fraction
FROM dbo.PPInvoiceDetail d JOIN dbo.PPInvoice h ON h.ID = d.PPInvoiceID
WHERE h.CompanyID = @company AND h.[Date] >= @from AND h.[Date] < DATEADD(DAY,1,@to) AND d.VATRate > 0;
```
- Mong đợi: `VATAmount` khớp đúng một trong hai cột `expected_*` (xác nhận cả đơn vị lẫn cách làm tròn).

## 3. `TotalAmount` đã gồm thuế chưa (rule `DOC_TOTAL_MISMATCH`)
```sql
SELECT TOP 20 h.ID, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount,
       (SELECT SUM(d.Amount) FROM dbo.PPInvoiceDetail d WHERE d.PPInvoiceID = h.ID) AS sum_lines,
       (SELECT SUM(d.VATAmount) FROM dbo.PPInvoiceDetail d WHERE d.PPInvoiceID = h.ID) AS sum_vat
FROM dbo.PPInvoice h
WHERE h.CompanyID = @company AND h.[Date] >= @from AND h.[Date] < DATEADD(DAY,1,@to);
```
- `TotalAmount ≈ sum_lines` → **chưa gồm thuế**; `TotalAmount ≈ sum_lines + sum_vat` → **đã gồm thuế** (rule hiện giả định cái này).
- Lặp lại với `dbo.SAInvoice` / `dbo.SAInvoiceDetail` nếu có hóa đơn bán.

## 4. Cột hóa đơn ở chứng từ phiếu chi / báo Nợ / hàng trả lại
```sql
SELECT TOP 10 d.InvoiceTemplate, d.InvoiceSeries, d.InvoiceNo, d.InvoiceDate FROM dbo.MCPaymentDetail d
JOIN dbo.MCPayment h ON h.ID = d.MCPaymentID WHERE h.CompanyID = @company AND d.InvoiceNo IS NOT NULL;
SELECT TOP 10 InvoiceTemplate, InvoiceSeries, InvoiceNo, InvoiceDate FROM dbo.PPDiscountReturn
WHERE CompanyID = @company AND InvoiceNo IS NOT NULL;
SELECT TOP 10 InvoiceTemplate, InvoiceSeries, InvoiceNo, InvoiceDate FROM dbo.SAReturn
WHERE CompanyID = @company AND InvoiceNo IS NOT NULL;
```
- Lỗi "Invalid column name" nghĩa là cột đó **không tồn tại** ở bảng ấy → gửi lỗi để tôi sửa `sql/erp/<loai>-*.sql`.

## 5. Mỗi loại chứng từ có chạy được không (tên cột)
Chạy từng câu; chỉ cần báo câu nào **lỗi**.
```sql
SELECT TOP 3 ID, CompanyID, TypeID, [Date], PostedDate, NoFBook, Reason, CurrencyID, ExchangeRate, TotalAmount, TotalVATAmount, Recorded, AccountingObjectID, AccountingObjectName, TaxCode FROM dbo.MBTellerPaper WHERE CompanyID=@company;
SELECT TOP 3 ID, CompanyID, TypeID, [Date], PostedDate, NoFBook, Reason, CurrencyID, ExchangeRate, TotalAmount, Recorded, AccountingObjectID, AccountingObjectName FROM dbo.MBDeposit WHERE CompanyID=@company;
SELECT TOP 3 ID, CompanyID, TypeID, [Date], PostedDate, NoFBook, Reason, CurrencyID, ExchangeRate, TotalAmount, Recorded, AccountingObjectID, AccountingObjectName FROM dbo.MCReceipt WHERE CompanyID=@company;
SELECT TOP 3 ID, CompanyID, TypeID, [Date], PostedDate, NoFBook, Reason, CurrencyID, ExchangeRate, TotalAmount, Recorded, AccountingObjectID, AccountingObjectName FROM dbo.PPDiscountReturn WHERE CompanyID=@company;
SELECT TOP 3 ID, CompanyID, TypeID, [Date], PostedDate, NoFBook, Reason, CurrencyID, ExchangeRate, TotalAmount, Recorded, AccountingObjectID, AccountingObjectName FROM dbo.SAReturn WHERE CompanyID=@company;
```
- Dòng chi tiết: lặp với `dbo.<Bang>Detail` và các cột `OrderPriority, Description, DebitAccount, CreditAccount, Amount, AmountOriginal`.

## 6. Mã loại chứng từ thực tế (TypeID)
```sql
SELECT 'MBTellerPaper' AS tbl, TypeID, COUNT(*) n FROM dbo.MBTellerPaper WHERE CompanyID=@company GROUP BY TypeID
UNION ALL SELECT 'MBDeposit', TypeID, COUNT(*) FROM dbo.MBDeposit WHERE CompanyID=@company GROUP BY TypeID
UNION ALL SELECT 'MCReceipt', TypeID, COUNT(*) FROM dbo.MCReceipt WHERE CompanyID=@company GROUP BY TypeID
UNION ALL SELECT 'MCPayment', TypeID, COUNT(*) FROM dbo.MCPayment WHERE CompanyID=@company GROUP BY TypeID
UNION ALL SELECT 'PPDiscountReturn', TypeID, COUNT(*) FROM dbo.PPDiscountReturn WHERE CompanyID=@company GROUP BY TypeID
UNION ALL SELECT 'SAReturn', TypeID, COUNT(*) FROM dbo.SAReturn WHERE CompanyID=@company GROUP BY TypeID;
```
- Mong đợi: TypeID nằm trong các dải ở `docs/erp-mapping.md` §11. Giá trị ngoài dải → chứng từ đó **không được liệt kê**.

## 7. Giảm giá bán hàng (TypeID 340–345 nằm ở bảng nào)
```sql
SELECT t.name AS tbl, COUNT(*) AS n_columns FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
WHERE t.name LIKE '%Discount%' OR t.name LIKE '%SAReturn%' GROUP BY t.name;
SELECT TOP 5 TypeID FROM dbo.SAReturn WHERE TypeID BETWEEN 340 AND 345 AND CompanyID=@company;
```

## 8. Công nợ 331 (rule `XDOC_PAYMENT_EXCEEDS_DEBT`)
```sql
SELECT TOP 20 AccountingObjectID, SUM(CreditAmount - DebitAmount) AS debt
FROM dbo.GeneralLedgerDetail
WHERE CompanyID=@company AND Account LIKE '331%' AND PostedDate < DATEADD(DAY,1,@to)
GROUP BY AccountingObjectID ORDER BY debt DESC;
-- Có số dư đầu kỳ riêng không?
SELECT TOP 5 * FROM dbo.SummaryOpeningBalance WHERE CompanyID=@company;
```
- Mong đợi: `debt` dương = còn phải trả. Nếu âm toàn bộ → đổi dấu trong `rule-payment-exceeds-debt.sql`.
- Nếu số dư đầu kỳ chỉ ở `SummaryOpeningBalance`, cần cộng thêm vào truy vấn.
- **Nặng:** thu hẹp `@to` / thêm `AND AccountingObjectID = '...'` nếu chậm.

## 9. Phương thức thanh toán của hóa đơn mua
Adapter hiện **không đọc** cột phương thức thanh toán của `PPInvoice` (mặc định `UNKNOWN`). Tìm cột phù hợp:
```sql
SELECT c.name FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
WHERE t.name='PPInvoice' AND (c.name LIKE '%Payment%' OR c.name LIKE '%Pay%');
```
- Gửi tên cột (nếu có) và các giá trị thực tế (`SELECT TOP 20 <cot>, COUNT(*) FROM dbo.PPInvoice GROUP BY <cot>`) để ánh xạ về `CASH` / `BANK_TRANSFER`; rule `REG_CASH_PAYMENT_THRESHOLD` cần việc này.

## Sau khi có kết quả
Gửi lại (1) kết quả mục 1–3, 8 và 9, (2) tên câu bị lỗi ở mục 4–5, (3) TypeID lạ ở mục 6–7. Tôi sẽ cập nhật SQL/rule, bật rule và thêm test theo số liệu thật.
