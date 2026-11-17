package vn.edu.uet.erpai.knowledge.service;

import java.util.UUID;

/** Sự kiện nội bộ: yêu cầu nạp tài liệu (xử lý bất đồng bộ sau khi commit). */
public record IngestRequested(UUID documentId) {}
