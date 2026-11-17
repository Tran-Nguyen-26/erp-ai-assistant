package vn.edu.uet.erpai.knowledge.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument;

public interface KnowledgeDocumentRepository
    extends JpaRepository<KnowledgeDocument, UUID>, JpaSpecificationExecutor<KnowledgeDocument> {}
