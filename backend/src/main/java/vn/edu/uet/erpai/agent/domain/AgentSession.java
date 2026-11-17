package vn.edu.uet.erpai.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_session")
public class AgentSession {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "company_id", nullable = false)
  private String companyId;

  @Column(nullable = false)
  private String channel = "CHAT";

  private String title;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_activity_at", nullable = false)
  private OffsetDateTime lastActivityAt;

  protected AgentSession() {}

  public AgentSession(UUID userId, String companyId, String channel, String title) {
    this.userId = userId;
    this.companyId = companyId;
    this.channel = channel;
    this.title = title;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
    lastActivityAt = createdAt;
  }

  public void touch(String titleIfEmpty) {
    lastActivityAt = OffsetDateTime.now();
    if (title == null && titleIfEmpty != null) {
      title = titleIfEmpty.length() > 80 ? titleIfEmpty.substring(0, 80) : titleIfEmpty;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCompanyId() {
    return companyId;
  }

  public String getChannel() {
    return channel;
  }

  public String getTitle() {
    return title;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getLastActivityAt() {
    return lastActivityAt;
  }
}
