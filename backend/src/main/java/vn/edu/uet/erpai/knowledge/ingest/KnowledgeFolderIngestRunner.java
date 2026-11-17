package vn.edu.uet.erpai.knowledge.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.edu.uet.erpai.common.storage.FileStorageService;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.DocType;
import vn.edu.uet.erpai.knowledge.repository.KnowledgeDocumentRepository;
import vn.edu.uet.erpai.knowledge.service.IngestionService;

/**
 * Profile {@code ingest}: nạp mọi tài liệu trong {@code knowledge/} có file {@code .meta.yml} đi
 * kèm.
 */
@Component
@Profile("ingest")
public class KnowledgeFolderIngestRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeFolderIngestRunner.class);

  private final KnowledgeDocumentRepository documents;
  private final FileStorageService storage;
  private final IngestionService ingestion;
  private final JdbcTemplate jdbc;
  private final Path folder;

  public KnowledgeFolderIngestRunner(
      KnowledgeDocumentRepository documents,
      FileStorageService storage,
      IngestionService ingestion,
      JdbcTemplate jdbc,
      @Value("${app.knowledge.dir:../knowledge}") String dir) {
    this.documents = documents;
    this.storage = storage;
    this.ingestion = ingestion;
    this.jdbc = jdbc;
    this.folder = Path.of(dir);
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    if (!Files.isDirectory(folder)) {
      log.warn("Không có thư mục tri thức: {}", folder.toAbsolutePath());
      return;
    }
    try (Stream<Path> files = Files.walk(folder)) {
      for (var meta :
          files.filter(p -> p.getFileName().toString().endsWith(".meta.yml")).toList()) {
        ingestOne(meta);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void ingestOne(Path metaFile) throws IOException {
    var stem = metaFile.getFileName().toString().replace(".meta.yml", "");
    Path source;
    try (Stream<Path> siblings = Files.list(metaFile.getParent())) {
      source =
          siblings
              .filter(p -> p.getFileName().toString().startsWith(stem + "."))
              .filter(p -> !p.getFileName().toString().endsWith(".meta.yml"))
              .findFirst()
              .orElse(null);
    }
    if (source == null) {
      log.warn("Thiếu file nội dung cho {}", metaFile);
      return;
    }
    Map<String, Object> meta;
    try (var in = Files.newInputStream(metaFile)) {
      meta = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    }
    var content = Files.readAllBytes(source);
    var sha = FileStorageService.sha256(content);
    Integer existing =
        jdbc.queryForObject(
            "SELECT count(*) FROM knowledge_document d JOIN uploaded_file f ON f.id = d.file_id"
                + " WHERE f.sha256 = ? AND d.status = 'READY'",
            Integer.class,
            sha);
    if (existing != null && existing > 0) {
      log.info("Bỏ qua (đã nạp): {}", source.getFileName());
      return;
    }
    var stored = storage.save(content, source.getFileName().toString(), null, "KNOWLEDGE", null);
    var doc =
        documents.save(
            new KnowledgeDocument(
                String.valueOf(meta.get("title")),
                (String) meta.get("docNumber"),
                DocType.valueOf(String.valueOf(meta.get("docType"))),
                date(meta.get("issuedDate")),
                date(meta.get("effectiveFrom")),
                date(meta.get("effectiveTo")),
                (String) meta.get("sourceUrl"),
                stored.id()));
    ingestion.ingest(doc.getId());
  }

  private static LocalDate date(Object v) {
    if (v == null) return null;
    if (v instanceof java.util.Date d) {
      return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
    return LocalDate.parse(v.toString());
  }
}
