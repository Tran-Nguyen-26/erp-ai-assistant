package vn.edu.uet.erpai.rule.service;

import java.util.List;

public record CheckResultResponse(
    String entityRef, int checkedRules, List<ViolationResponse> violations, long durationMs) {}
