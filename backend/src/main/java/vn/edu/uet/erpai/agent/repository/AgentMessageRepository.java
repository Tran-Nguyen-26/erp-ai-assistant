package vn.edu.uet.erpai.agent.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.uet.erpai.agent.domain.AgentMessage;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {

  Page<AgentMessage> findBySessionIdOrderBySeqAsc(UUID sessionId, Pageable pageable);

  @Query("select coalesce(max(m.seq), 0) from AgentMessage m where m.sessionId = ?1")
  int maxSeq(UUID sessionId);

  /** Hội thoại gần nhất (chỉ USER/ASSISTANT), mới → cũ. */
  List<AgentMessage> findTop20BySessionIdAndRoleInOrderBySeqDesc(
      UUID sessionId, List<String> roles);

  void deleteBySessionId(UUID sessionId);
}
