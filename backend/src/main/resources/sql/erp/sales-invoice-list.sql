-- Danh sách hóa đơn bán hàng trong kỳ. Dùng index SAInvoice(Date, CompanyID, ...).
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       h.InvoiceTemplate, h.InvoiceSeries, h.InvoiceNo, h.InvoiceDate, h.PaymentMethod,
       ao.AccountingObjectCode AS PartnerCode, ao.TaxCode AS PartnerTaxCode
FROM dbo.SAInvoice h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
