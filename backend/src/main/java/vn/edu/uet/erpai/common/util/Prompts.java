package vn.edu.uet.erpai.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Nạp prompt từ resources/prompts; dòng đầu là "# version: <phiên bản>". */
public final class Prompts {

  public record Prompt(String version, String body) {}

  private Prompts() {}

  public static Prompt load(String name) {
    try (var in = new ClassPathResource("prompts/" + name).getInputStream()) {
      var raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      int nl = raw.indexOf('\n');
      return new Prompt(
          raw.substring(0, nl).replace("# version:", "").trim(), raw.substring(nl + 1).strip());
    } catch (IOException e) {
      throw new IllegalStateException("Không đọc được prompt " + name, e);
    }
  }
}
