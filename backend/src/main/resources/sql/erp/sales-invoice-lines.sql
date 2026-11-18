-- Dòng hóa đơn bán hàng. Index: SAInvoiceDetail(DatePartition, SAInvoiceID); :partitionDate là Date của header.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.MaterialGoodsID, d.Quantity, d.UnitPrice, d.VATRate, d.VATAmount, d.VATAccount
FROM dbo.SAInvoiceDetail d
JOIN dbo.SAInvoice h ON h.ID = d.SAInvoiceID
WHERE h.CompanyID = :companyId
  AND d.SAInvoiceID = :id
  AND d.DatePartition = :partitionDate
ORDER BY d.OrderPriority
