export type Severity = 'ERROR' | 'WARNING' | 'INFO';
export type ViolationStatus =
  'OPEN' | 'EXPLAINED' | 'PROPOSED' | 'RESOLVED_PENDING_ERP' | 'RESOLVED' | 'DISMISSED';

export interface ViolationResponse {
  id: string;
  ruleId: string;
  ruleName: string;
  severity: Severity;
  status: ViolationStatus;
  entityType: string;
  entityRef: string;
  entityNo: string | null;
  entityDate: string | null;
  message: string;
  evidence: Record<string, unknown>;
  hasExplanation: boolean;
  pendingProposalId: string | null;
  dismissReason: string | null;
  detectedAt: string;
}

export interface CheckResultResponse {
  entityRef: string;
  checkedRules: number;
  violations: ViolationResponse[];
  durationMs: number;
}

export interface CitationResponse {
  index: number;
  chunkId: string;
  documentId: string;
  docNumber: string | null;
  headingPath: string;
  quote: string;
  effectiveFrom: string | null;
}

export interface ExplanationResponse {
  violationId: string;
  explanation: {
    id: string;
    summary: string;
    explanation: string;
    citations: CitationResponse[];
    supportStatus: 'SUPPORTED' | 'PARTIAL' | 'UNSUPPORTED';
    confidence: number;
    model: string;
    promptVersion: string;
    cached: boolean;
    createdAt: string;
  };
  proposal: ProposalResponse | null;
  proposalSkippedReason: string | null;
}

export interface ProposalChange {
  path: string;
  from: string | null;
  to: string | null;
}

export interface ProposalResponse {
  id: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED' | 'EXPORTED';
  violation: { id: string; ruleId: string | null; severity: string | null };
  entityType: string;
  entityRef: string;
  entityNo: string | null;
  actionType: string;
  changes: ProposalChange[];
  rationale: string;
  citations: CitationResponse[];
  verification: {
    passed: boolean;
    rulesChecked: string[];
    remainingViolations: { ruleId: string; message: string }[];
    introducedViolations: { ruleId: string; message: string }[];
  };
  createdByType: 'AGENT' | 'USER';
  createdAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
}

export interface ViolationSummary {
  total: number;
  bySeverity: Record<string, number>;
  byStatus: Record<string, number | undefined>;
  topRules: { ruleId: string; count: number }[];
}
