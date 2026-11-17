package vn.edu.uet.erpai.review.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.review.domain.Proposal;

/** Xuất CSV (UTF-8 BOM, mở được bằng Excel) liệt kê từng thay đổi được duyệt. */
@Component
public class ExportOnlyWriteBack implements ErpWriteBackPort {

  private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

  @Override
  public ExportedFile export(List<Proposal> proposals) {
    var sb = new StringBuilder("﻿");
    sb.append(
        "proposalId,entityType,entityRef,path,from,to,rationale,reviewedBy,reviewedAt,reviewNote\n");
    for (var p : proposals) {
      for (var c : p.getChanges()) {
        sb.append(csv(p.getId()))
            .append(',')
            .append(csv(p.getEntityType()))
            .append(',')
            .append(csv(p.getEntityRef()))
            .append(',')
            .append(csv(c.get("path")))
            .append(',')
            .append(csv(c.get("from")))
            .append(',')
            .append(csv(c.get("to")))
            .append(',')
            .append(csv(p.getRationale()))
            .append(',')
            .append(csv(p.getReviewedBy()))
            .append(',')
            .append(csv(p.getReviewedAt()))
            .append(',')
            .append(csv(p.getReviewNote()))
            .append('\n');
      }
    }
    return new ExportedFile(
        "phieu-de-nghi-dieu-chinh-" + LocalDate.now() + ".csv",
        "text/csv; charset=UTF-8",
        sb.toString().getBytes(StandardCharsets.UTF_8));
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
}
