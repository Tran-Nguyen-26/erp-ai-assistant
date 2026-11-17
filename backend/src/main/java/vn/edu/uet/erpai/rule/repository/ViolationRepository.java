package vn.edu.uet.erpai.rule.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import vn.edu.uet.erpai.rule.domain.Violation;
import vn.edu.uet.erpai.rule.domain.ViolationStatus;

public interface ViolationRepository
    extends JpaRepository<Violation, UUID>, JpaSpecificationExecutor<Violation> {

  Optional<Violation> findByFingerprint(String fingerprint);

  @Override
  @EntityGraph(attributePaths = "rule")
  Page<Violation> findAll(Specification<Violation> spec, Pageable pageable);

  @EntityGraph(attributePaths = "rule")
  List<Violation> findByCompanyIdAndEntityTypeAndEntityRefAndStatusIn(
      String companyId, String entityType, String entityRef, Collection<ViolationStatus> statuses);

  @EntityGraph(attributePaths = "rule")
  Optional<Violation> findByIdAndCompanyId(UUID id, String companyId);

  @Query("select v.rule.id, count(v) from Violation v where v.status in ?1 group by v.rule.id")
  List<Object[]> countByRule(Collection<ViolationStatus> statuses);

  @Query(
      "select v.severity, count(v) from Violation v where v.reviewJobId = ?1 group by v.severity")
  List<Object[]> countSeverityByJob(UUID reviewJobId);

  @Query("select v.status, count(v) from Violation v where v.companyId = ?1 group by v.status")
  List<Object[]> countByStatus(String companyId);

  @Query(
      "select v.severity, count(v) from Violation v where v.companyId = ?1 and v.status in ?2"
          + " group by v.severity")
  List<Object[]> countBySeverity(String companyId, Collection<ViolationStatus> statuses);

  @Query(
      "select v.rule.ruleId, count(v) as c from Violation v where v.companyId = ?1 and v.status"
          + " in ?2 group by v.rule.ruleId order by c desc")
  List<Object[]> topRules(String companyId, Collection<ViolationStatus> statuses);
}
