-- Hàng mua trả lại (220-222) và giảm giá (230-232) — bảng PPDiscountReturn. Thông tin hóa đơn nằm ở header.
SELECT COUNT_BIG(*) AS Total
FROM dbo.PPDiscountReturn h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
  AND (:q = '' OR h.NoFBook LIKE :qLike ESCAPE '\' OR h.Reason LIKE :qLike ESCAPE '\' OR h.AccountingObjectName LIKE :qLike ESCAPE '\')
  AND (:partnerTaxCode = '' OR ao.TaxCode = :partnerTaxCode)
