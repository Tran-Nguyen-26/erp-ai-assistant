-- Hàng mua trả lại (220-222) và giảm giá (230-232) — bảng PPDiscountReturn. Thông tin hóa đơn nằm ở header.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount AS TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       ao.TaxCode AS PartnerTaxCode, ao.AccountingObjectCode AS PartnerCode,
       h.InvoiceTemplate AS InvoiceTemplate, h.InvoiceSeries AS InvoiceSeries, h.InvoiceNo AS InvoiceNo, h.InvoiceDate AS InvoiceDate, h.PaymentMethod AS PaymentMethod
FROM dbo.PPDiscountReturn h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
