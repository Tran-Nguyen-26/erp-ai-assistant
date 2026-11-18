-- Phiếu thu tiền mặt (MCReceipt). Dòng chi tiết không có cột thuế/hóa đơn.
SELECT COUNT_BIG(*) AS Total
FROM dbo.MCReceipt h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
  AND (:q = '' OR h.NoFBook LIKE :qLike ESCAPE '\' OR h.Reason LIKE :qLike ESCAPE '\' OR h.AccountingObjectName LIKE :qLike ESCAPE '\')
  AND (:partnerTaxCode = '' OR h.TaxCode = :partnerTaxCode)
