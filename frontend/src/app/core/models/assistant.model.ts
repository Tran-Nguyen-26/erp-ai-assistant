export interface SessionResponse {
  id: string;
  channel: string;
  title: string | null;
  createdAt: string;
  lastActivityAt: string;
}

export interface CitationRef {
  index: number;
  chunkId: string;
  docNumber: string | null;
  headingPath: string;
}

export interface MessageResponse {
  id: string;
  seq: number;
  role: 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM';
  content: string | null;
  toolName: string | null;
  toolArgs: Record<string, unknown> | null;
  toolResult: { status?: string; summary?: string } | null;
  citations: CitationRef[];
  createdAt: string;
}

/** Sự kiện SSE của một lượt chat (docs/api-spec.md §10). */
export type ChatEvent =
  | { event: 'message_start'; data: { messageId: string; sessionId: string } }
  | { event: 'tool_call'; data: { tool: string; args: Record<string, unknown> } }
  | { event: 'tool_result'; data: { tool: string; status: string; summary: string } }
  | { event: 'token'; data: { text: string } }
  | { event: 'citations'; data: { citations: CitationRef[] } }
  | { event: 'done'; data: { messageId: string; latencyMs: number; steps: number } }
  | { event: 'error'; data: { code: string; message: string } };
