-- Báo Nợ ngân hàng: ủy nhiệm chi (120-128), séc chuyển khoản (130-135), séc tiền mặt (140-145) — bảng MBTellerPaper.
SELECT CAST(h.ID AS NVARCHAR(36)) AS ID
FROM dbo.MBTellerPaper h
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
ORDER BY h.[Date], h.ID
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
