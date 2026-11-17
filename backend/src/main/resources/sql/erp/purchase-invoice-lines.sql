-- Dòng chi tiết hóa đơn mua hàng. Index: PPInvoiceDetail(PPInvoiceID).
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.MaterialGoodsID, d.Quantity, d.UnitPrice, d.VATRate, d.VATAmount, d.VATAccount,
       d.InvoiceTemplate, d.InvoiceSeries, d.InvoiceNo, d.InvoiceDate
FROM dbo.PPInvoiceDetail d
JOIN dbo.PPInvoice h ON h.ID = d.PPInvoiceID
WHERE h.CompanyID = :companyId
  AND d.PPInvoiceID = :id
ORDER BY d.OrderPriority
