package vn.edu.uet.erpai.erp.model;

import java.util.List;

public record ErpPage<T>(List<T> items, int page, int size, long totalItems) {}
