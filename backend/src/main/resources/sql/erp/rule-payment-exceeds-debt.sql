-- Rule XDOC_PAYMENT_EXCEEDS_DEBT: số dư phải trả (TK 331*) của đối tượng tại ngày chứng từ, không tính chính chứng từ này.
-- Chỉ trả về hàng khi số tiền thanh toán (các dòng Nợ 331*) lớn hơn số dư. Dùng index GLD(CompanyID, PostedDate).
SELECT s.debt, :debtPaymentAmount AS paying
FROM (
    SELECT COALESCE(SUM(g.CreditAmount - g.DebitAmount), 0) AS debt
    FROM dbo.GeneralLedgerDetail g
    WHERE g.CompanyID = :companyId
      AND g.AccountingObjectID = :partnerId
      AND g.Account LIKE '331%'
      AND g.PostedDate < DATEADD(DAY, 1, :voucherDate)
      AND (g.ReferenceID IS NULL OR g.ReferenceID <> :entityRef)
) s
WHERE :debtPaymentAmount > s.debt
