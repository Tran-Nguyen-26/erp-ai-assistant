-- Dự phòng khi DatePartition không khớp Date của header: truy vấn không lọc phân vùng (chậm, chỉ dùng khi cần).
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.MaterialGoodsID, d.Quantity, d.UnitPrice, d.VATRate, d.VATAmount, d.VATAccount
FROM dbo.SAInvoiceDetail d
JOIN dbo.SAInvoice h ON h.ID = d.SAInvoiceID
WHERE h.CompanyID = :companyId
  AND d.SAInvoiceID = :id
ORDER BY d.OrderPriority
