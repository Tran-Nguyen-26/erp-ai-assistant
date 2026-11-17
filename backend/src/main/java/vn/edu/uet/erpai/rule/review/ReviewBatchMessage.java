package vn.edu.uet.erpai.rule.review;

import java.util.List;
import java.util.UUID;

/** Một batch (≤ 200 chứng từ) của job rà soát. */
public record ReviewBatchMessage(
    UUID jobId, String companyId, String entityType, int batchNo, List<String> entityRefs) {}
