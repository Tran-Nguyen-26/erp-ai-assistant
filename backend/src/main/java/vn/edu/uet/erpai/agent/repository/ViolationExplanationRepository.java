package vn.edu.uet.erpai.agent.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.agent.domain.ViolationExplanation;

public interface ViolationExplanationRepository extends JpaRepository<ViolationExplanation, UUID> {

  Optional<ViolationExplanation> findFirstByViolationIdAndPromptVersionOrderByCreatedAtDesc(
      UUID violationId, String promptVersion);
}
