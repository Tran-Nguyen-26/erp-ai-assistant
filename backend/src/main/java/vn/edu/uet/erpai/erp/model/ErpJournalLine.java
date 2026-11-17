package vn.edu.uet.erpai.erp.model;

import java.math.BigDecimal;

public record ErpJournalLine(
    int lineNo,
    String description,
    String debitAccount,
    String creditAccount,
    BigDecimal amount,
    BigDecimal amountOriginal,
    String materialGoodsId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal vatRate,
    BigDecimal vatAmount,
    String vatAccount) {}
