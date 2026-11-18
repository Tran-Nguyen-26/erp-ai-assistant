package vn.edu.uet.erpai.eval;

import java.util.Set;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

/** Một chứng từ tổng hợp kèm nhãn: tập rule PHẢI báo (ground truth). */
public record EvalCase(ErpVoucher voucher, Set<String> expectedRuleIds) {}
