-- Phiếu thu tiền mặt (MCReceipt). Dòng chi tiết không có cột thuế/hóa đơn.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, CAST(0 AS DECIMAL(18,2)) AS TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       h.TaxCode AS PartnerTaxCode, ao.AccountingObjectCode AS PartnerCode,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate, CAST(NULL AS NVARCHAR(100)) AS PaymentMethod
FROM dbo.MCReceipt h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
