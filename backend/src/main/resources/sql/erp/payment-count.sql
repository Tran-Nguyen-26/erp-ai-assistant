-- Phiếu chi tiền mặt (MCPayment). Không có index theo ngày: quét theo CompanyID, luôn phân trang.
SELECT COUNT_BIG(*) AS Total
FROM dbo.MCPayment h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
  AND (:q = '' OR h.NoFBook LIKE :qLike ESCAPE '\' OR h.Reason LIKE :qLike ESCAPE '\' OR h.AccountingObjectName LIKE :qLike ESCAPE '\')
  AND (:partnerTaxCode = '' OR h.TaxCode = :partnerTaxCode)
