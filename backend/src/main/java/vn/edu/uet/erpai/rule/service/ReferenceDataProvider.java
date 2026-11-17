package vn.edu.uet.erpai.rule.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.engine.RuleReferenceData;

/**
 * Nạp danh mục tài khoản của công ty từ ERP cho rule (có cache ngắn hạn). Lỗi ERP → dữ liệu rỗng.
 */
@Component
public class ReferenceDataProvider {

  private static final Logger log = LoggerFactory.getLogger(ReferenceDataProvider.class);
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final int PAGE = 100;
  private static final int MAX_PAGES = 50;

  private record Cached(RuleReferenceData data, Instant at) {}

  private final ErpQueryService erp;
  private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

  public ReferenceDataProvider(ErpQueryService erp) {
    this.erp = erp;
  }

  public RuleReferenceData forCompany(String companyId) {
    var c = cache.get(companyId);
    if (c != null && c.at().plus(TTL).isAfter(Instant.now())) {
      return c.data();
    }
    try {
      var all = new HashSet<String>();
      var detail = new HashSet<String>();
      for (int page = 0; page < MAX_PAGES; page++) {
        var batch = erp.listAccounts(companyId, page, PAGE);
        for (var a : batch) {
          if (a.isActive() && a.accountNumber() != null) {
            all.add(a.accountNumber());
            if (a.isDetail()) {
              detail.add(a.accountNumber());
            }
          }
        }
        if (batch.size() < PAGE) {
          break;
        }
      }
      var data = new RuleReferenceData(all, detail);
      cache.put(companyId, new Cached(data, Instant.now()));
      return data;
    } catch (RuntimeException e) {
      log.warn("Không nạp được danh mục tài khoản: {}", e.getMessage());
      return RuleReferenceData.EMPTY; // rule phải coi là "không biết", không báo sai
    }
  }
}
