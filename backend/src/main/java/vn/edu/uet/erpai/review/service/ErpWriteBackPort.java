package vn.edu.uet.erpai.review.service;

import java.util.List;
import vn.edu.uet.erpai.review.domain.Proposal;

/**
 * Cổng đưa đề xuất đã duyệt ra ngoài hệ thống. Mặc định chỉ XUẤT phiếu đề nghị điều chỉnh; hệ thống
 * KHÔNG ghi ngược ERP (ADR 0003). Có thể thay bằng cài đặt khác trong tương lai.
 */
public interface ErpWriteBackPort {

  record ExportedFile(String fileName, String contentType, byte[] content) {}

  ExportedFile export(List<Proposal> approvedProposals, ExportFormat format);
}
