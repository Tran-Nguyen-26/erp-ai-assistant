-- Danh sách phiếu chi trong kỳ. MCPayment không có index theo ngày: quét theo CompanyID, luôn phân trang.
SELECT h.ID, h.CompanyID, h.TypeID, h.[Date], h.PostedDate, h.NoFBook, h.Reason,
       h.CurrencyID, h.ExchangeRate, h.TotalAmount, h.TotalVATAmount,
       h.Recorded, h.AccountingObjectID, h.AccountingObjectName, h.TaxCode,
       ao.AccountingObjectCode AS PartnerCode
FROM dbo.MCPayment h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
