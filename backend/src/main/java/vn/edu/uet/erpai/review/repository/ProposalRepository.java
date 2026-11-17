package vn.edu.uet.erpai.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.uet.erpai.review.domain.Proposal;
import vn.edu.uet.erpai.review.domain.Proposal.Status;

public interface ProposalRepository
    extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {

  Optional<Proposal> findByIdAndCompanyId(UUID id, String companyId);

  List<Proposal> findByViolationIdAndStatus(UUID violationId, Status status);

  List<Proposal> findByIdInAndCompanyId(Collection<UUID> ids, String companyId);
}
