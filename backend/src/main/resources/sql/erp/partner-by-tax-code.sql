-- Tra cứu đối tượng (NCC/khách hàng) theo MST trong một công ty. Chưa có thông tin index của AccountingObject
-- theo TaxCode nên giới hạn TOP 1 và luôn kèm CompanyID.
SELECT TOP 1 CAST(ao.ID AS NVARCHAR(36)) AS ID, ao.AccountingObjectCode, ao.AccountingObjectName, ao.TaxCode
FROM dbo.AccountingObject ao
WHERE ao.CompanyID = :companyId
  AND ao.TaxCode = :taxCode
ORDER BY ao.AccountingObjectCode
