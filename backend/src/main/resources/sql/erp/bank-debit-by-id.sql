-- Báo Nợ ngân hàng: ủy nhiệm chi (120-128), séc chuyển khoản (130-135), séc tiền mặt (140-145) — bảng MBTellerPaper.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, CAST(0 AS DECIMAL(18,2)) AS TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       h.TaxCode AS PartnerTaxCode, ao.AccountingObjectCode AS PartnerCode,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate, CAST(NULL AS NVARCHAR(100)) AS PaymentMethod
FROM dbo.MBTellerPaper h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
