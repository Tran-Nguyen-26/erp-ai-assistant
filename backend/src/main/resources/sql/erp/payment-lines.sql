-- Phiếu chi tiền mặt (MCPayment). Không có index theo ngày: quét theo CompanyID, luôn phân trang.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       CAST(NULL AS UNIQUEIDENTIFIER) AS MaterialGoodsID, CAST(NULL AS DECIMAL(18,4)) AS Quantity, CAST(NULL AS DECIMAL(18,4)) AS UnitPrice,
       d.VATRate AS VATRate, d.VATAmount AS VATAmount, d.VATAccount AS VATAccount,
       d.InvoiceTemplate AS InvoiceTemplate, d.InvoiceSeries AS InvoiceSeries, d.InvoiceNo AS InvoiceNo, d.InvoiceDate AS InvoiceDate
FROM dbo.MCPaymentDetail d
JOIN dbo.MCPayment h ON h.ID = d.MCPaymentID
WHERE h.CompanyID = :companyId
  AND d.MCPaymentID = :id
ORDER BY d.OrderPriority
