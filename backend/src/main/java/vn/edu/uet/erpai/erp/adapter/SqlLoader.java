package vn.edu.uet.erpai.erp.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Nạp câu SQL từ {@code resources/sql/erp/<name>.sql}. */
@Component
public class SqlLoader {

  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public String load(String name) {
    return cache.computeIfAbsent(name, this::read);
  }

  private String read(String name) {
    try (var in = new ClassPathResource("sql/erp/" + name + ".sql").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Không đọc được SQL ERP: " + name, e);
    }
  }
}
