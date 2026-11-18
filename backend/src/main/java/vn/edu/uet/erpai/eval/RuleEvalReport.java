package vn.edu.uet.erpai.eval;

import java.util.Map;

/** Kết quả đánh giá rule: số liệu theo từng rule và tổng, kèm thông lượng. */
public record RuleEvalReport(
    int vouchers,
    long elapsedMillis,
    double vouchersPerSecond,
    Map<String, RuleStats> perRule,
    RuleStats overall) {

  public record RuleStats(int truePositives, int falsePositives, int falseNegatives) {
    public double precision() {
      int d = truePositives + falsePositives;
      return d == 0 ? 1.0 : (double) truePositives / d;
    }

    public double recall() {
      int d = truePositives + falseNegatives;
      return d == 0 ? 1.0 : (double) truePositives / d;
    }

    public double f1() {
      double p = precision();
      double r = recall();
      return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }
  }
}
