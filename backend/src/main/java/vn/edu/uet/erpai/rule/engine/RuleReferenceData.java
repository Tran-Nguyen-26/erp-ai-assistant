package vn.edu.uet.erpai.rule.engine;

import java.util.Map;
import java.util.Set;

/**
 * Dữ liệu tham chiếu của công ty được nạp sẵn cho rule thuần (không I/O trong lúc đánh giá): danh
 * mục tài khoản đang dùng. Rỗng khi chưa nạp được (rule phải xử lý như "không biết", không báo
 * sai).
 */
public record RuleReferenceData(
    Set<String> allAccounts, Set<String> detailAccounts, Map<String, String> accountNames) {

  public static final RuleReferenceData EMPTY = new RuleReferenceData(Set.of(), Set.of());

  public RuleReferenceData(Set<String> allAccounts, Set<String> detailAccounts) {
    this(allAccounts, detailAccounts, Map.of());
  }

  public RuleReferenceData {
    allAccounts = Set.copyOf(allAccounts);
    detailAccounts = Set.copyOf(detailAccounts);
    accountNames = Map.copyOf(accountNames);
  }
}
