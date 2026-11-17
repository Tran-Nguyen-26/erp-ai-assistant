package vn.edu.uet.erpai.agent.session;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.agent.domain.AgentMessage;
import vn.edu.uet.erpai.agent.domain.AgentSession;
import vn.edu.uet.erpai.agent.repository.AgentMessageRepository;
import vn.edu.uet.erpai.agent.repository.AgentSessionRepository;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;

/** Phiên hội thoại: mỗi người dùng chỉ thấy phiên của chính mình. */
@Service
public class AgentSessionService {

  private final AgentSessionRepository sessions;
  private final AgentMessageRepository messages;

  public AgentSessionService(AgentSessionRepository sessions, AgentMessageRepository messages) {
    this.sessions = sessions;
    this.messages = messages;
  }

  @Transactional
  public AgentSession create(CurrentUser user, String channel, String title) {
    var ch = channel == null || channel.isBlank() ? "CHAT" : channel;
    if (!List.of("CHAT", "ENTRY", "REVIEW").contains(ch)) {
      throw new BusinessException(
          "ASSISTANT_CHANNEL_INVALID", "channel phải là CHAT, ENTRY hoặc REVIEW");
    }
    return sessions.save(new AgentSession(user.id(), user.companyId(), ch, title));
  }

  @Transactional(readOnly = true)
  public Page<AgentSession> list(CurrentUser user, Pageable pageable) {
    return sessions.findByUserId(user.id(), pageable);
  }

  @Transactional(readOnly = true)
  public AgentSession require(CurrentUser user, UUID id) {
    return sessions
        .findByIdAndUserId(id, user.id())
        .orElseThrow(
            () ->
                new BusinessException(
                    "ASSISTANT_SESSION_NOT_FOUND", "Không tìm thấy phiên", HttpStatus.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Page<AgentMessage> messages(CurrentUser user, UUID sessionId, Pageable pageable) {
    require(user, sessionId);
    return messages.findBySessionIdOrderBySeqAsc(sessionId, pageable);
  }

  @Transactional
  public void delete(CurrentUser user, UUID id) {
    require(user, id);
    messages.deleteBySessionId(id);
    sessions.deleteById(id);
  }

  /** Lịch sử USER/ASSISTANT gần nhất (cũ → mới), tối đa {@code window} tin. */
  @Transactional(readOnly = true)
  public List<AgentMessage> history(UUID sessionId, int window) {
    var recent =
        messages.findTop20BySessionIdAndRoleInOrderBySeqDesc(
            sessionId, List.of("USER", "ASSISTANT"));
    var limited = recent.subList(0, Math.min(window, recent.size()));
    return limited.reversed();
  }

  @Transactional
  public AgentMessage append(AgentMessage message) {
    return messages.save(message);
  }

  @Transactional
  public int nextSeq(UUID sessionId) {
    return messages.maxSeq(sessionId) + 1;
  }

  @Transactional
  public void touch(UUID sessionId, String titleHint) {
    sessions.findById(sessionId).ifPresent(s -> s.touch(titleHint));
  }
}
