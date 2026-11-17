package vn.edu.uet.erpai.rule.engine;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;

/**
 * Đánh giá rule SpEL trên chứng từ canonical. Ngữ cảnh bị khóa: không {@code T(...)}, không
 * constructor, không bean, method chỉ gọi được trên kiểu trong allow-list, property chỉ đọc.
 */
@Component
public class SpelRuleEvaluator {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(.+?)\\s*}}");
  private static final Pattern MODEL_PACKAGE =
      Pattern.compile("^vn\\.edu\\.uet\\.erpai\\.erp\\.model\\..*");

  private final SpelExpressionParser parser = new SpelExpressionParser();

  public List<RuleResult> evaluate(RuleDefinition rule, ErpVoucher voucher) {
    return evaluate(rule, voucher, RuleReferenceData.EMPTY);
  }

  public List<RuleResult> evaluate(
      RuleDefinition rule, ErpVoucher voucher, RuleReferenceData reference) {
    if (!rule.appliesTo(voucher.entityType())) {
      return List.of();
    }
    var condition = parser.parseExpression(rule.condition());
    var results = new ArrayList<RuleResult>();
    var items = items(rule, voucher);
    for (int i = 0; i < items.size(); i++) {
      var item = items.get(i);
      var ctx = context(rule, voucher, item, reference);
      Boolean violated = condition.getValue(ctx, Boolean.class);
      if (Boolean.TRUE.equals(violated)) {
        var evidence = evidence(rule, ctx);
        var key = evidenceKey(rule, ctx, rule.forEach() == null ? null : String.valueOf(i + 1));
        var scope = new LinkedHashMap<String, Object>(evidence);
        scope.put("entity", voucher);
        scope.put("item", item);
        scope.put("params", rule.params());
        results.add(
            new RuleResult(
                rule.ruleId(),
                rule.severity(),
                voucher.ref(),
                render(rule.message(), scope),
                evidence,
                key,
                Fingerprint.of(rule.ruleId(), voucher.entityType().name(), voucher.ref(), key)));
      }
    }
    return results;
  }

  private static List<Object> items(RuleDefinition rule, ErpVoucher v) {
    if (rule.forEach() == null) {
      return java.util.Collections.singletonList(null);
    }
    if ("lines".equals(rule.forEach())) {
      return new ArrayList<>(v.lines());
    }
    throw new IllegalStateException(rule.ruleId() + ": forEach không hỗ trợ: " + rule.forEach());
  }

  private StandardEvaluationContext context(
      RuleDefinition rule, ErpVoucher v, Object item, RuleReferenceData reference) {
    var ctx = new StandardEvaluationContext(v);
    ctx.setPropertyAccessors(List.<PropertyAccessor>of(new RecordAccessor(), new MapAccessor()));
    ctx.setConstructorResolvers(List.of());
    ctx.setMethodResolvers(List.of(new AllowListMethodResolver()));
    ctx.setTypeLocator(
        name -> {
          throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, name);
        });
    ctx.setVariable("entity", v);
    ctx.setVariable("item", item);
    ctx.setVariable("params", rule.params());
    ctx.setVariable("allAccounts", reference.allAccounts());
    ctx.setVariable("detailAccounts", reference.detailAccounts());
    register(ctx, "abs", "abs", java.math.BigDecimal.class);
    register(ctx, "round", "round", java.math.BigDecimal.class);
    register(ctx, "sum", "sum", Collection.class);
    register(ctx, "isBlank", "isBlank", String.class);
    register(ctx, "matches", "matches", String.class, String.class);
    return ctx;
  }

  private static void register(
      StandardEvaluationContext ctx, String name, String method, Class<?>... types) {
    try {
      Method m = RuleFunctions.class.getMethod(method, types);
      ctx.registerFunction(name, m);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> evidence(RuleDefinition rule, EvaluationContext ctx) {
    var out = new LinkedHashMap<String, Object>();
    rule.evidence().forEach((k, expr) -> out.put(k, parser.parseExpression(expr).getValue(ctx)));
    return out;
  }

  private String evidenceKey(RuleDefinition rule, EvaluationContext ctx, String fallback) {
    if (rule.evidenceKey() != null) {
      return String.valueOf(parser.parseExpression(rule.evidenceKey()).getValue(ctx));
    }
    return fallback;
  }

  /**
   * Thay {{biểu thức}} trong message bằng giá trị SpEL trên scope (evidence, entity, item, params).
   */
  String render(String template, Map<String, Object> scope) {
    var ctx = new StandardEvaluationContext(scope);
    ctx.setPropertyAccessors(List.<PropertyAccessor>of(new RecordAccessor(), new MapAccessor()));
    ctx.setConstructorResolvers(List.of());
    ctx.setMethodResolvers(List.of(new AllowListMethodResolver()));
    ctx.setTypeLocator(
        name -> {
          throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, name);
        });
    var m = PLACEHOLDER.matcher(template.strip().replaceAll("\\s+", " "));
    var sb = new StringBuilder();
    while (m.find()) {
      Object value = parser.parseExpression(m.group(1)).getValue(ctx);
      m.appendReplacement(sb, Matcher.quoteReplacement(format(value)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String format(Object v) {
    if (v instanceof java.math.BigDecimal b) {
      return b.stripTrailingZeros().toPlainString();
    }
    return String.valueOf(v);
  }

  /** Đọc thành phần của record (record không có getX()). Chỉ đọc. */
  static final class RecordAccessor implements PropertyAccessor {
    @Override
    public Class<?>[] getSpecificTargetClasses() {
      return null;
    }

    @Override
    public boolean canRead(EvaluationContext ctx, Object target, String name) {
      return target != null
          && target.getClass().isRecord()
          && MODEL_PACKAGE.matcher(target.getClass().getName()).matches()
          && component(target, name) != null;
    }

    @Override
    public TypedValue read(EvaluationContext ctx, Object target, String name)
        throws AccessException {
      try {
        return new TypedValue(component(target, name).getAccessor().invoke(target));
      } catch (ReflectiveOperationException e) {
        throw new AccessException("Không đọc được " + name, e);
      }
    }

    @Override
    public boolean canWrite(EvaluationContext ctx, Object target, String name) {
      return false;
    }

    @Override
    public void write(EvaluationContext ctx, Object target, String name, Object newValue) {
      throw new UnsupportedOperationException("Rule không được ghi dữ liệu");
    }

    private static RecordComponent component(Object target, String name) {
      for (var c : target.getClass().getRecordComponents()) {
        if (c.getName().equals(name)) {
          return c;
        }
      }
      return null;
    }
  }

  /** Chỉ cho gọi method trên BigDecimal/String/Collection/Map; cấm getClass và tương tự. */
  static final class AllowListMethodResolver implements MethodResolver {
    private final ReflectiveMethodResolver delegate = new ReflectiveMethodResolver();

    @Override
    public MethodExecutor resolve(
        EvaluationContext ctx, Object target, String name, List<TypeDescriptor> argumentTypes)
        throws AccessException {
      boolean allowedType =
          target instanceof java.math.BigDecimal
              || target instanceof String
              || target instanceof Collection<?>
              || target instanceof Map<?, ?>;
      if (!allowedType || name.equals("getClass")) {
        throw new AccessException(
            "Không cho phép gọi "
                + name
                + " trên "
                + (target == null ? "null" : target.getClass().getSimpleName()));
      }
      try {
        return delegate.resolve(ctx, target, name, argumentTypes);
      } catch (EvaluationException e) {
        throw new AccessException(e.getMessage(), e);
      }
    }
  }
}
