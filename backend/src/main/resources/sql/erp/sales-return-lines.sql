-- Hàng bán trả lại (330-335) — bảng SAReturn (~7 nghìn dòng). Thông tin hóa đơn ở header. TypeID giảm giá bán (340-345) chưa xác nhận nằm ở bảng nào.
SELECT d.OrderPriority, d.Description, d.DebitAccount, d.CreditAccount, d.Amount, d.AmountOriginal,
       d.MaterialGoodsID AS MaterialGoodsID, d.Quantity AS Quantity, d.UnitPrice AS UnitPrice,
       d.VATRate AS VATRate, d.VATAmount AS VATAmount, d.VATAccount AS VATAccount,
       CAST(NULL AS NVARCHAR(100)) AS InvoiceTemplate, CAST(NULL AS NVARCHAR(100)) AS InvoiceSeries, CAST(NULL AS NVARCHAR(100)) AS InvoiceNo, CAST(NULL AS DATETIME) AS InvoiceDate
FROM dbo.SAReturnDetail d
JOIN dbo.SAReturn h ON h.ID = d.SAReturnID
WHERE h.CompanyID = :companyId
  AND d.SAReturnID = :id
ORDER BY d.OrderPriority
