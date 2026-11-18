-- Dòng hạch toán phiếu chi. Nối theo MCPaymentID.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.VATRate, d.VATAmount, d.VATAccount, d.InvoiceTemplate, d.InvoiceSeries, d.InvoiceNo, d.InvoiceDate
FROM dbo.MCPaymentDetail d
JOIN dbo.MCPayment h ON h.ID = d.MCPaymentID
WHERE h.CompanyID = :companyId
  AND d.MCPaymentID = :id
ORDER BY d.OrderPriority
