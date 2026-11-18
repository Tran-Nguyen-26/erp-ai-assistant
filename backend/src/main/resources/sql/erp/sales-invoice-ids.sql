-- Khóa hóa đơn bán hàng dạng GUID@yyyy-MM-dd (kèm ngày để tra cứu nhanh theo PK (Date, ID)).
SELECT CAST(h.ID AS NVARCHAR(36)) + '@' + CONVERT(NVARCHAR(10), h.[Date], 23) AS ID
FROM dbo.SAInvoice h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
