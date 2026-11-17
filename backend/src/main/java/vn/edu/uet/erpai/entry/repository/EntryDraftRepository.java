package vn.edu.uet.erpai.entry.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.entry.domain.EntryDraft;

public interface EntryDraftRepository extends JpaRepository<EntryDraft, UUID> {

  Optional<EntryDraft> findByIdAndCompanyId(UUID id, String companyId);

  Page<EntryDraft> findByCompanyIdAndStatusNot(
      String companyId, EntryDraft.Status status, Pageable pageable);

  Page<EntryDraft> findByCompanyIdAndCreatedByAndStatusNot(
      String companyId, UUID createdBy, EntryDraft.Status status, Pageable pageable);
}
