package vn.edu.uet.erpai.rule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.definition.RuleLoader;
import vn.edu.uet.erpai.rule.domain.RuleRecord;
import vn.edu.uet.erpai.rule.repository.RuleRecordRepository;

/**
 * Đồng bộ rule YAML → bảng rule_definition. Giữ lại cấu hình admin (enabled/severity/params) khi
 * YAML thay đổi: chỉ cập nhật giá trị nào admin chưa chỉnh khỏi mặc định YAML cũ.
 */
@Service
public class RuleSyncService implements ApplicationRunner {

  public record SyncReport(
      List<String> added, List<String> updated, int unchanged, List<String> errors) {}

  private final RuleLoader loader;
  private final RuleRecordRepository repository;
  private final RuleCatalog catalog;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

  public RuleSyncService(RuleLoader loader, RuleRecordRepository repository, RuleCatalog catalog) {
    this.loader = loader;
    this.repository = repository;
    this.catalog = catalog;
  }

  @Override
  public void run(ApplicationArguments args) {
    sync();
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public SyncReport sync() {
    var added = new ArrayList<String>();
    var updated = new ArrayList<String>();
    var errors = new ArrayList<String>();
    int unchanged = 0;
    List<RuleDefinition> yaml;
    try {
      yaml = loader.loadAll();
    } catch (RuntimeException e) {
      return new SyncReport(List.of(), List.of(), 0, List.of(e.getMessage()));
    }
    for (var def : yaml) {
      var definition = mapper.convertValue(def, Map.class);
      var hash = hash(definition);
      var existing = repository.findByRuleId(def.ruleId()).orElse(null);
      if (existing == null) {
        var rec = new RuleRecord(def.ruleId());
        apply(rec, def, definition, hash);
        rec.setConfig(def.severity(), def.isEnabled(), new HashMap<>(def.params()));
        rec.setVersion(def.version());
        repository.save(rec);
        added.add(def.ruleId());
      } else if (!hash.equals(existing.getSourceHash())) {
        var oldYaml = existing.getDefinition();
        var oldSeverity = Severity.valueOf((String) oldYaml.get("severity"));
        var oldEnabled = Boolean.TRUE.equals(oldYaml.get("enabled"));
        var oldParams = (Map<String, Object>) oldYaml.getOrDefault("params", Map.of());
        // admin đã chỉnh khỏi mặc định cũ → giữ; ngược lại theo YAML mới
        var severity =
            existing.getSeverity() != oldSeverity ? existing.getSeverity() : def.severity();
        var enabled = existing.isEnabled() != oldEnabled ? existing.isEnabled() : def.isEnabled();
        var params = new HashMap<String, Object>(def.params());
        existing
            .getParams()
            .forEach(
                (k, v) -> {
                  if (def.params().containsKey(k) && !Objects.equals(v, oldParams.get(k))) {
                    params.put(k, v);
                  }
                });
        apply(existing, def, definition, hash);
        existing.setConfig(severity, enabled, params);
        existing.setVersion(Math.max(existing.getVersion() + 1, def.version()));
        updated.add(def.ruleId());
      } else {
        unchanged++;
      }
    }
    catalog.refresh();
    return new SyncReport(added, updated, unchanged, errors);
  }

  private static void apply(
      RuleRecord rec, RuleDefinition def, Map<String, Object> definition, String hash) {
    rec.applyYaml(
        def.name(),
        def.group(),
        def.entityType().name(),
        def.evaluator(),
        definition,
        def.legalRefs(),
        def.ragQuery(),
        hash);
  }

  private String hash(Map<?, ?> definition) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(
              md.digest(mapper.writeValueAsString(definition).getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
