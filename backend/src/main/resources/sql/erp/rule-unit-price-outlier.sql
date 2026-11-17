-- Rule ANM_UNIT_PRICE_OUTLIER: so đơn giá của một dòng với lịch sử mua cùng vật tư từ cùng nhà cung cấp.
-- Chỉ trả về hàng khi có đủ mẫu và đơn giá lệch hơn :k độ lệch chuẩn. Dùng index PPInvoice(CompanyID, AccountingObjectID, ...).
SELECT s.avgPrice, s.stdPrice, s.n
FROM (
    SELECT AVG(d.UnitPrice) AS avgPrice, STDEV(d.UnitPrice) AS stdPrice, COUNT(*) AS n
    FROM dbo.PPInvoiceDetail d
    JOIN dbo.PPInvoice h ON h.ID = d.PPInvoiceID
    WHERE h.CompanyID = :companyId
      AND h.AccountingObjectID = :partnerId
      AND d.MaterialGoodsID = :materialGoodsId
      AND h.[Date] >= :sinceDate
      AND h.ID <> :entityRef
      AND d.UnitPrice > 0
) s
WHERE s.n >= :minSamples
  AND s.stdPrice > 0
  AND ABS(:unitPrice - s.avgPrice) > :k * s.stdPrice
