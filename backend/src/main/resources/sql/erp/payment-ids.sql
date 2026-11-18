-- Phiếu chi tiền mặt (MCPayment). Không có index theo ngày: quét theo CompanyID, luôn phân trang.
SELECT CAST(h.ID AS NVARCHAR(36)) AS ID
FROM dbo.MCPayment h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
