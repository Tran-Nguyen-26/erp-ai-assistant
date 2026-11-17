-- Header hóa đơn mua hàng theo ID. Index: PK(ID) + CompanyID.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount, h.TotalDiscountAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName,
       ao.AccountingObjectCode AS PartnerCode, ao.TaxCode AS PartnerTaxCode
FROM dbo.PPInvoice h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.ID = :id
