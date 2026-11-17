package vn.edu.uet.erpai.knowledge.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class IngestionListener {

  private final IngestionService ingestion;

  public IngestionListener(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onIngestRequested(IngestRequested event) {
    ingestion.ingest(event.documentId());
  }
}
