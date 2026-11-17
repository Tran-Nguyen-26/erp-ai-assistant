package vn.edu.uet.erpai.rule.review;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJobRepository extends JpaRepository<ReviewJob, UUID> {

  Optional<ReviewJob> findByIdAndCompanyId(UUID id, String companyId);

  boolean existsByIdAndCompanyId(UUID id, String companyId);

  Page<ReviewJob> findByCompanyId(String companyId, Pageable pageable);

  Page<ReviewJob> findByCompanyIdAndStatus(String companyId, String status, Pageable pageable);

  boolean existsByCompanyIdAndPeriodFromAndPeriodToAndStatusIn(
      String companyId, LocalDate from, LocalDate to, Collection<String> statuses);
}
