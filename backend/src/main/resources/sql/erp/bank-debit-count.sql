-- Báo Nợ ngân hàng: ủy nhiệm chi (120-128), séc chuyển khoản (130-135), séc tiền mặt (140-145) — bảng MBTellerPaper.
SELECT COUNT_BIG(*) AS Total
FROM dbo.MBTellerPaper h
LEFT JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND h.[Date] >= :periodFrom
  AND h.[Date] < :periodToExclusive
  AND h.TypeID IN (:typeIds)
  AND (:q = '' OR h.NoFBook LIKE :qLike ESCAPE '\' OR h.Reason LIKE :qLike ESCAPE '\' OR h.AccountingObjectName LIKE :qLike ESCAPE '\')
  AND (:partnerTaxCode = '' OR ao.TaxCode = :partnerTaxCode)
