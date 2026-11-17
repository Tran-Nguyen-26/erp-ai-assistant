package vn.edu.uet.erpai.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.common.exception.BusinessException;

/**
 * Lưu file upload xuống đĩa (tên ngẫu nhiên, không dùng tên gốc làm đường dẫn) + bảng
 * uploaded_file.
 */
@Service
public class FileStorageService {

  public record StoredFile(UUID id, String sha256, String storagePath) {}

  private final JdbcTemplate jdbc;
  private final Path root;

  public FileStorageService(JdbcTemplate jdbc, @Value("${app.storage.dir}") String dir) {
    this.jdbc = jdbc;
    this.root = Path.of(dir).toAbsolutePath().normalize();
  }

  public StoredFile save(
      byte[] content, String originalName, String contentType, String purpose, UUID uploadedBy) {
    try {
      Files.createDirectories(root);
      var id = UUID.randomUUID();
      var ext = extension(originalName);
      var file = root.resolve(id + (ext.isEmpty() ? "" : "." + ext)).normalize();
      if (!file.startsWith(root)) {
        throw new BusinessException("FILE_INVALID_NAME", "Tên file không hợp lệ");
      }
      Files.write(file, content);
      var sha = sha256(content);
      jdbc.update(
          "INSERT INTO uploaded_file (id, original_name, content_type, size_bytes, sha256,"
              + " storage_path, purpose, uploaded_by) VALUES (?,?,?,?,?,?,?,?)",
          id,
          safeName(originalName),
          contentType == null ? "application/octet-stream" : contentType,
          content.length,
          sha,
          file.toString(),
          purpose,
          uploadedBy);
      return new StoredFile(id, sha, file.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public byte[] read(UUID fileId) {
    var path =
        jdbc
            .query(
                "SELECT storage_path FROM uploaded_file WHERE id = ?",
                (rs, i) -> rs.getString(1),
                fileId)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        "FILE_NOT_FOUND", "Không tìm thấy file", HttpStatus.NOT_FOUND));
    try {
      return Files.readAllBytes(Path.of(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String originalName(UUID fileId) {
    return jdbc.queryForObject(
        "SELECT original_name FROM uploaded_file WHERE id = ?", String.class, fileId);
  }

  public static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String extension(String name) {
    if (name == null || !name.contains(".")) {
      return "";
    }
    var ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    return ext.matches("[a-z0-9]{1,5}") ? ext : "";
  }

  private static String safeName(String name) {
    var n = name == null ? "file" : Path.of(name).getFileName().toString();
    return n.length() > 250 ? n.substring(0, 250) : n;
  }
}
