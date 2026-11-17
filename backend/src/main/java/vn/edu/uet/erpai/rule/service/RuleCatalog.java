package vn.edu.uet.erpai.rule.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.definition.RuleLoader;
import vn.edu.uet.erpai.rule.repository.RuleRecordRepository;

/**
 * Tập rule hiệu lực = định nghĩa YAML + cấu hình admin trong DB. Có cache, làm mới khi sửa rule.
 */
@Component
public class RuleCatalog {

  /** Rule hiệu lực kèm id & version DB (để gắn vào violation). */
  public record ActiveRule(UUID dbId, int version, RuleDefinition definition) {}

  private final RuleLoader loader;
  private final RuleRecordRepository repository;
  private final AtomicReference<List<ActiveRule>> cache = new AtomicReference<>();

  public RuleCatalog(RuleLoader loader, RuleRecordRepository repository) {
    this.loader = loader;
    this.repository = repository;
  }

  public List<ActiveRule> all() {
    var c = cache.get();
    return c != null ? c : refresh();
  }

  @Transactional(readOnly = true)
  public List<ActiveRule> refresh() {
    Map<String, RuleDefinition> yaml =
        loader.loadAll().stream()
            .collect(Collectors.toMap(RuleDefinition::ruleId, Function.identity()));
    var list =
        repository.findAll().stream()
            .filter(r -> yaml.containsKey(r.getRuleId()))
            .map(
                r ->
                    new ActiveRule(
                        r.getId(),
                        r.getVersion(),
                        yaml.get(r.getRuleId())
                            .withOverrides(r.getSeverity(), r.isEnabled(), r.getParams())))
            .toList();
    cache.set(list);
    return list;
  }
}
