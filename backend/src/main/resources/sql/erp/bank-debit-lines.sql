-- Báo Nợ ngân hàng: ủy nhiệm chi (120-128), séc chuyển khoản (130-135), séc tiền mặt (140-145) — bảng MBTellerPaper.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       CAST(NULL AS UNIQUEIDENTIFIER) AS MaterialGoodsID, CAST(NULL AS DECIMAL(18,4)) AS Quantity, CAST(NULL AS DECIMAL(18,4)) AS UnitPrice,
       d.VATRate AS VATRate, d.VATAmount AS VATAmount, d.VATAccount AS VATAccount,
       d.InvoiceTemplate AS InvoiceTemplate, d.InvoiceSeries AS InvoiceSeries, d.InvoiceNo AS InvoiceNo, d.InvoiceDate AS InvoiceDate
FROM dbo.MBTellerPaperDetail d
JOIN dbo.MBTellerPaper h ON h.ID = d.MBTellerPaperID
WHERE h.CompanyID = :companyId
  AND d.MBTellerPaperID = :id
ORDER BY d.OrderPriority
