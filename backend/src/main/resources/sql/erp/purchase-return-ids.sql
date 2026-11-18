-- Hàng mua trả lại (220-222) và giảm giá (230-232) — bảng PPDiscountReturn. Thông tin hóa đơn nằm ở header.
SELECT CAST(h.ID AS NVARCHAR(36)) AS ID
FROM dbo.PPDiscountReturn h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
