package vn.edu.uet.erpai.rule.definition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Nạp và kiểm tra rule từ classpath. File sai định dạng làm app dừng sớm (fail-fast). */
@Component
public class RuleLoader {

  private static final Pattern RULE_ID = Pattern.compile("^(FLD|DOC|XDOC|REG|ANM)_[A-Z0-9_]+$");

  /** Tham số rule SQL cho phép lấy từ chứng từ (không bao giờ lấy từ chuỗi người dùng). */
  public static final java.util.Set<String> ALLOWED_SQL_PARAMS =
      java.util.Set.of(
          "companyId",
          "sellerTaxCode",
          "invoiceSeries",
          "invoiceNo",
          "entityRef",
          "partnerId",
          "sinceDate",
          "lineNo",
          "materialGoodsId",
          "unitPrice");

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  public List<RuleDefinition> loadAll() {
    return loadFrom("classpath*:rules/**/*.yml");
  }

  public List<RuleDefinition> loadFrom(String pattern) {
    var rules = new ArrayList<RuleDefinition>();
    var seen = new HashSet<String>();
    try {
      for (var resource : new PathMatchingResourcePatternResolver().getResources(pattern)) {
        try (var in = resource.getInputStream()) {
          Map<String, Object> raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
          var rule = mapper.convertValue(raw, RuleDefinition.class);
          validate(rule, resource.getFilename());
          if (!seen.add(rule.ruleId())) {
            throw new IllegalStateException("Trùng ruleId: " + rule.ruleId());
          }
          rules.add(rule);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Không đọc được rule", e);
    }
    return List.copyOf(rules);
  }

  private static void validate(RuleDefinition r, String file) {
    if (r.ruleId() == null || !RULE_ID.matcher(r.ruleId()).matches()) {
      throw new IllegalStateException(file + ": ruleId sai quy ước (FLD_/DOC_/XDOC_/REG_/ANM_)");
    }
    if (!r.ruleId().startsWith(r.group().name() + "_")) {
      throw new IllegalStateException(file + ": tiền tố ruleId không khớp group");
    }
    if (r.severity() == null || r.entityType() == null || r.evaluator() == null) {
      throw new IllegalStateException(file + ": thiếu severity/entityType/evaluator");
    }
    if (r.evaluator() == RuleDefinition.Evaluator.SPEL
        && (r.condition() == null || r.condition().isBlank())) {
      throw new IllegalStateException(file + ": rule SPEL thiếu condition");
    }
    if (r.evaluator() == RuleDefinition.Evaluator.SQL) {
      if (r.sqlRef() == null || !r.sqlRef().matches("^rule-[a-z0-9-]+$")) {
        throw new IllegalStateException(file + ": rule SQL cần sqlRef dạng rule-<ten-kebab>");
      }
      for (var p : r.sqlParams()) {
        // hoặc là tham số lấy từ chứng từ, hoặc là khóa trong params của chính rule (k,
        // minSamples...)
        if (!ALLOWED_SQL_PARAMS.contains(p) && !r.params().containsKey(p)) {
          throw new IllegalStateException(
              file + ": sqlParams chỉ được gồm " + ALLOWED_SQL_PARAMS + " hoặc khóa trong params");
        }
      }
    }
    if (r.message() == null || r.message().isBlank()) {
      throw new IllegalStateException(file + ": thiếu message");
    }
  }
}
