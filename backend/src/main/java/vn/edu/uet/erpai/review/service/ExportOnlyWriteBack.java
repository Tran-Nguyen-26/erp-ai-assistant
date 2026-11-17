package vn.edu.uet.erpai.review.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.export.PdfTableWriter;
import vn.edu.uet.erpai.common.export.XlsxWriter;
import vn.edu.uet.erpai.review.domain.Proposal;

/**
 * Xuất phiếu đề nghị điều chỉnh (CSV, XLSX hoặc PDF) liệt kê từng thay đổi đã được duyệt. Hệ thống
 * KHÔNG ghi ngược ERP (ADR 0003): phiếu để kế toán chỉnh sửa thủ công trong ERP.
 */
@Component
public class ExportOnlyWriteBack implements ErpWriteBackPort {

  private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  static final List<String> HEADER =
      List.of(
          "Mã đề xuất",
          "Loại chứng từ",
          "Chứng từ (ERP)",
          "Trường",
          "Giá trị cũ",
          "Giá trị mới",
          "Lý do",
          "Người duyệt",
          "Thời điểm duyệt",
          "Ghi chú duyệt");

  @Override
  public ExportedFile export(List<Proposal> proposals, ExportFormat format) {
    var stamp = LocalDate.now().toString();
    return switch (format) {
      case CSV ->
          new ExportedFile(
              "phieu-de-nghi-dieu-chinh-" + stamp + ".csv",
              "text/csv; charset=UTF-8",
              csv(proposals));
      case XLSX -> {
        var rows = new ArrayList<List<String>>();
        rows.add(HEADER);
        rows.addAll(table(proposals));
        yield new ExportedFile(
            "phieu-de-nghi-dieu-chinh-" + stamp + ".xlsx",
            XlsxWriter.CONTENT_TYPE,
            XlsxWriter.write("Đề nghị điều chỉnh", rows, Set.of(0)));
      }
      case PDF ->
          new ExportedFile(
              "phieu-de-nghi-dieu-chinh-" + stamp + ".pdf",
              PdfTableWriter.CONTENT_TYPE,
              PdfTableWriter.write(
                  "PHIẾU ĐỀ NGHỊ ĐIỀU CHỈNH DỮ LIỆU KẾ TOÁN",
                  "Ngày lập: "
                      + stamp
                      + " · "
                      + proposals.size()
                      + " đề xuất đã duyệt · Không tự động ghi vào ERP",
                  HEADER,
                  List.of(1.2f, 1.1f, 1.5f, 1.4f, 1.1f, 1.1f, 1.8f, 1.1f, 1.1f, 1.4f),
                  table(proposals)));
    };
  }

  private static List<List<String>> table(List<Proposal> proposals) {
    var rows = new ArrayList<List<String>>();
    for (var p : proposals) {
      for (var c : p.getChanges()) {
        rows.add(
            List.of(
                str(p.getId()),
                str(p.getEntityType()),
                str(p.getEntityRef()),
                str(c.get("path")),
                str(c.get("from")),
                str(c.get("to")),
                str(p.getRationale()),
                str(p.getReviewedBy()),
                p.getReviewedAt() == null
                    ? ""
                    : p.getReviewedAt().atZoneSameInstant(ZoneId.systemDefault()).format(TIME),
                str(p.getReviewNote())));
      }
    }
    return rows;
  }

  private static byte[] csv(List<Proposal> proposals) {
    var sb = new StringBuilder("﻿");
    sb.append(String.join(",", HEADER.stream().map(ExportOnlyWriteBack::csv).toList()))
        .append('\n');
    for (var row : table(proposals)) {
      sb.append(String.join(",", row.stream().map(ExportOnlyWriteBack::csv).toList())).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Escape CSV và chặn CSV injection (ô bắt đầu bằng = + @ hoặc - không phải số). */
  static String csv(Object value) {
    if (value == null) {
      return "";
    }
    var s = value.toString();
    boolean dangerous =
        s.startsWith("=")
            || s.startsWith("+")
            || s.startsWith("@")
            || s.startsWith("\t")
            || s.startsWith("\r")
            || (s.startsWith("-") && !NUMBER.matcher(s).matches());
    if (dangerous) {
      s = "'" + s;
    }
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private static String str(Object o) {
    return o == null ? "" : o.toString();
  }
}
