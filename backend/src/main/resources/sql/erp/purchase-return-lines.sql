-- Hàng mua trả lại (220-222) và giảm giá (230-232) — bảng PPDiscountReturn. Thông tin hóa đơn nằm ở header.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.MaterialGoodsID AS MaterialGoodsID, d.Quantity AS Quantity, d.UnitPrice AS UnitPrice,
       d.VATRate AS VATRate, d.VATAmount AS VATAmount, d.VATAccount AS VATAccount,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate
FROM dbo.PPDiscountReturnDetail d
JOIN dbo.PPDiscountReturn h ON h.ID = d.PPDiscountReturnID
WHERE h.CompanyID = :companyId
  AND d.PPDiscountReturnID = :id
ORDER BY d.OrderPriority
