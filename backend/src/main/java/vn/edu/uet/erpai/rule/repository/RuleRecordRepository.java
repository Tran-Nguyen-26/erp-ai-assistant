package vn.edu.uet.erpai.rule.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.rule.domain.RuleRecord;

public interface RuleRecordRepository extends JpaRepository<RuleRecord, UUID> {

  Optional<RuleRecord> findByRuleId(String ruleId);

  List<RuleRecord> findByRuleIdIn(Collection<String> ruleIds);
}
