export interface RuleResponse {
  ruleId: string;
  name: string;
  group: string;
  severity: string;
  entityType: string;
  evaluator: string;
  params: Record<string, unknown>;
  legalRefs: string[];
  ragQuery: string | null;
  enabled: boolean;
  version: number;
  openViolationCount: number;
  updatedAt: string;
  definition: Record<string, unknown> | null;
}

export interface ReviewJobResponse {
  id: string;
  periodFrom: string;
  periodTo: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  progress: {
    totalDocuments: number;
    processedDocuments: number;
    percent: number;
    failedBatches: number;
  };
  violationCount: number;
  summary: Record<string, number>;
  startedAt: string | null;
  finishedAt: string | null;
  totalBatches: number;
}

export interface KnowledgeDocumentResponse {
  id: string;
  title: string;
  docNumber: string | null;
  docType: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  status: 'PENDING' | 'INGESTING' | 'READY' | 'FAILED' | 'ARCHIVED';
  chunkCount: number;
  errorMessage: string | null;
}

export interface RetrievedChunk {
  chunkId: string;
  docNumber: string | null;
  docTitle: string;
  headingPath: string;
  content: string;
  rrfScore: number;
  rerankScore: number | null;
}

export interface RetrievalResult {
  chunks: RetrievedChunk[];
  maxScore: number;
  sufficient: boolean;
}

export interface AuditLogResponse {
  id: number;
  occurredAt: string;
  actorType: string;
  actor: { id: string; username: string | null } | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: Record<string, unknown>;
  traceId: string | null;
}

export interface DashboardStats {
  violations: { total: number; open: number; resolved: number; bySeverity: Record<string, number> };
  topRules: { ruleId: string; name: string; count: number }[];
  proposals: { pending: number; approved: number; rejected: number; acceptanceRate: number };
  ai: {
    explanations: number;
    supportedRate: number;
    avgLatencyMs: number;
    positiveFeedbackRate: number;
  };
  entry: { drafts: number; confirmed: number; avgLowConfidenceFields: number };
}
