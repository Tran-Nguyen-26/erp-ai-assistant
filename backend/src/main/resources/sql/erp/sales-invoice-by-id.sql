-- Hóa đơn bán hàng theo ID. PK của SAInvoice là (Date, ID): khi biết ngày (ref dạng GUID@yyyy-MM-dd) truy vấn dùng
-- index theo Date; nếu không có ngày thì chỉ lọc CompanyID + ID (chậm hơn trên bảng lớn).
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       h.InvoiceTemplate, h.InvoiceSeries, h.InvoiceNo, h.InvoiceDate, h.PaymentMethod,
       ao.AccountingObjectCode AS PartnerCode, ao.TaxCode AS PartnerTaxCode
FROM dbo.SAInvoice h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
  AND (h.[Date] >= :dateFrom AND h.[Date] < :dateTo)
