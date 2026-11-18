-- Hàng bán trả lại (330-335) — bảng SAReturn (~7 nghìn dòng). Thông tin hóa đơn ở header. TypeID giảm giá bán (340-345) chưa xác nhận nằm ở bảng nào.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount AS TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       ao.TaxCode AS PartnerTaxCode, ao.AccountingObjectCode AS PartnerCode,
       h.InvoiceTemplate AS InvoiceTemplate, h.InvoiceSeries AS InvoiceSeries, h.InvoiceNo AS InvoiceNo, h.InvoiceDate AS InvoiceDate, h.PaymentMethod AS PaymentMethod
FROM dbo.SAReturn h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
