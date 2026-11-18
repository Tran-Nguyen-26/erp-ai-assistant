-- Phiếu chi tiền mặt theo ID (PK = ID).
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName, h.TaxCode,
       ao.AccountingObjectCode AS PartnerCode
FROM dbo.MCPayment h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
