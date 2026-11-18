-- Phiếu thu tiền mặt (MCReceipt). Dòng chi tiết không có cột thuế/hóa đơn.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       CAST(NULL AS UNIQUEIDENTIFIER) AS MaterialGoodsID, CAST(NULL AS DECIMAL(18,4)) AS Quantity, CAST(NULL AS DECIMAL(18,4)) AS UnitPrice,
       CAST(NULL AS DECIMAL(18,4)) AS VATRate, CAST(NULL AS DECIMAL(18,2)) AS VATAmount, CAST(NULL AS NVARCHAR(100)) AS VATAccount,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate
FROM dbo.MCReceiptDetail d
JOIN dbo.MCReceipt h ON h.ID = d.MCReceiptID
WHERE h.CompanyID = :companyId
  AND d.MCReceiptID = :id
ORDER BY d.OrderPriority
