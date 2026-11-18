-- Phiếu thu tiền mặt (MCReceipt). Dòng chi tiết không có cột thuế/hóa đơn.
SELECT CAST(h.ID AS NVARCHAR(36)) AS ID
FROM dbo.MCReceipt h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
