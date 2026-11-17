-- ID hóa đơn mua hàng trong kỳ (chỉ khóa, dùng chia batch rà soát). Quét theo CompanyID (PPInvoice không có index ngày).
SELECT h.ID
FROM dbo.PPInvoice h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
