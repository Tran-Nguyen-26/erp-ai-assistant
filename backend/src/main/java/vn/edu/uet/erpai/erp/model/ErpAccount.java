package vn.edu.uet.erpai.erp.model;

public record ErpAccount(
    String accountNumber, String accountName, boolean isDetail, boolean isActive) {}
