package vn.edu.uet.erpai.rule.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Fingerprint {

  private Fingerprint() {}

  /** SHA-256(ruleId|entityType|entityRef|evidenceKey) — chống trùng violation. */
  public static String of(String ruleId, String entityType, String entityRef, String evidenceKey) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var raw =
          ruleId
              + "|"
              + entityType
              + "|"
              + entityRef
              + "|"
              + (evidenceKey == null ? "" : evidenceKey);
      return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
