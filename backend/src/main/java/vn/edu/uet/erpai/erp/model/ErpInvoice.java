package vn.edu.uet.erpai.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ErpInvoice(
    String invoiceTemplate,
    String invoiceSeries,
    String invoiceNo,
    LocalDate invoiceDate,
    String sellerTaxCode,
    String sellerName,
    BigDecimal amount,
    BigDecimal vatAmount,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    String paymentMethod) {}
