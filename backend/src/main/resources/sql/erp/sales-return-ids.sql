-- Hàng bán trả lại (330-335) — bảng SAReturn (~7 nghìn dòng). Thông tin hóa đơn ở header. TypeID giảm giá bán (340-345) chưa xác nhận nằm ở bảng nào.
SELECT CAST(h.ID AS NVARCHAR(36)) AS ID
FROM dbo.SAReturn h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
