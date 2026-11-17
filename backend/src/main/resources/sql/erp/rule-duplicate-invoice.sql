-- Rule XDOC_DUPLICATE_INVOICE: tìm hóa đơn mua hàng đã ghi nhận trùng (MST bán, ký hiệu, số hóa đơn).
-- Truy vấn đi từ PPInvoice theo CompanyID (index) rồi nối chi tiết theo PPInvoiceID; PPInvoiceDetail không có
-- index theo số hóa đơn nên luôn giới hạn TOP 5 và phụ thuộc timeout truy vấn của ERP.
SELECT TOP 5 CAST(h.ID AS NVARCHAR(36)) AS ref, h.NoFBook AS voucherNo, h.[Date] AS voucherDate
FROM dbo.PPInvoice h
JOIN dbo.PPInvoiceDetail d ON d.PPInvoiceID = h.ID
JOIN dbo.AccountingObject ao ON ao.ID = h.AccountingObjectID
WHERE h.CompanyID = :companyId
  AND ao.TaxCode = :sellerTaxCode
  AND d.InvoiceSeries = :invoiceSeries
  AND d.InvoiceNo = :invoiceNo
  AND h.ID <> :entityRef
ORDER BY h.[Date] DESC
