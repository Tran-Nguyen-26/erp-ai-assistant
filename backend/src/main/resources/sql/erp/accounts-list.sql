-- Danh mục tài khoản của một công ty. Index: (AccountNumber, CompanyID), (CompanyID, ...).
SELECT a.AccountNumber, a.AccountName, a.IsParentNode, a.IsActive
FROM dbo.AccountList a
WHERE a.CompanyID = :companyId
ORDER BY a.AccountNumber
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
