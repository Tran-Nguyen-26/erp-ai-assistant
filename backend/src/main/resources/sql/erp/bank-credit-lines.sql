-- Báo Có ngân hàng / nộp tiền vào TK (MBDeposit).
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       CAST(NULL AS UNIQUEIDENTIFIER) AS MaterialGoodsID, CAST(NULL AS DECIMAL(18,4)) AS Quantity, CAST(NULL AS DECIMAL(18,4)) AS UnitPrice,
       CAST(NULL AS DECIMAL(18,4)) AS VATRate, CAST(NULL AS DECIMAL(18,2)) AS VATAmount, CAST(NULL AS NVARCHAR(100)) AS VATAccount,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate
FROM dbo.MBDepositDetail d
JOIN dbo.MBDeposit h ON h.ID = d.MBDepositID
WHERE h.CompanyID = :companyId
  AND d.MBDepositID = :id
ORDER BY d.OrderPriority
