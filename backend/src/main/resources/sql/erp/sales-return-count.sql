-- Hàng bán trả lại (330-335) — bảng SAReturn (~7 nghìn dòng). Thông tin hóa đơn ở header. TypeID giảm giá bán (340-345) chưa xác nhận nằm ở bảng nào.
SELECT COUNT_BIG(*) AS Total
FROM dbo.SAReturn h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
  AND (:q = '' OR h.NoFBook LIKE :qLike ESCAPE '\' OR h.Reason LIKE :qLike ESCAPE '\' OR h.AccountingObjectName LIKE :qLike ESCAPE '\')
  AND (:partnerTaxCode = '' OR ao.TaxCode = :partnerTaxCode)
