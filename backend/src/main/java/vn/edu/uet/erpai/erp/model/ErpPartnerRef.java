package vn.edu.uet.erpai.erp.model;

/** Đối tượng (nhà cung cấp/khách hàng) gắn với chứng từ. Chứa PII: mask khi dùng LLM cloud. */
public record ErpPartnerRef(String id, String code, String name, String taxCode) {}
