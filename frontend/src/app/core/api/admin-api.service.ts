import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuditLogResponse,
  DashboardStats,
  KnowledgeDocumentResponse,
  RetrievalResult,
  ReviewJobResponse,
  RuleResponse,
} from '../models/admin.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  rules(): Observable<RuleResponse[]> {
    return this.http.get<RuleResponse[]>(`${this.base}/rules`);
  }

  patchRule(
    ruleId: string,
    body: { enabled?: boolean; severity?: string; params?: Record<string, unknown> },
  ): Observable<RuleResponse> {
    return this.http.patch<RuleResponse>(`${this.base}/rules/${ruleId}`, body);
  }

  reloadRules(): Observable<{
    added: string[];
    updated: string[];
    unchanged: number;
    errors: string[];
  }> {
    return this.http.post<{
      added: string[];
      updated: string[];
      unchanged: number;
      errors: string[];
    }>(`${this.base}/rules/reload`, null);
  }

  documents(page = 0): Observable<PageResponse<KnowledgeDocumentResponse>> {
    return this.http.get<PageResponse<KnowledgeDocumentResponse>>(
      `${this.base}/knowledge/documents`,
      { params: new HttpParams().set('page', page) },
    );
  }

  uploadDocument(file: File, meta: Record<string, string>): Observable<KnowledgeDocumentResponse> {
    const form = new FormData();
    form.append('file', file);
    Object.entries(meta)
      .filter(([, v]) => v)
      .forEach(([k, v]) => form.append(k, v));
    return this.http.post<KnowledgeDocumentResponse>(`${this.base}/knowledge/documents`, form);
  }

  reindex(id: string): Observable<KnowledgeDocumentResponse> {
    return this.http.post<KnowledgeDocumentResponse>(
      `${this.base}/knowledge/documents/${id}/reindex`,
      null,
    );
  }

  searchKnowledge(query: string, asOf?: string): Observable<RetrievalResult> {
    return this.http.post<RetrievalResult>(`${this.base}/knowledge/search`, {
      query,
      asOf: asOf || null,
    });
  }

  auditLogs(action: string, page = 0): Observable<PageResponse<AuditLogResponse>> {
    let params = new HttpParams().set('page', page);
    if (action) {
      params = params.set('action', action);
    }
    return this.http.get<PageResponse<AuditLogResponse>>(`${this.base}/audit-logs`, { params });
  }

  stats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.base}/dashboard/stats`);
  }

  jobs(page = 0): Observable<PageResponse<ReviewJobResponse>> {
    return this.http.get<PageResponse<ReviewJobResponse>>(`${this.base}/review-jobs`, {
      params: new HttpParams().set('page', page),
    });
  }

  createJob(
    periodFrom: string,
    periodTo: string,
    entityTypes: string[] = ['PURCHASE_INVOICE'],
  ): Observable<ReviewJobResponse> {
    return this.http.post<ReviewJobResponse>(`${this.base}/review-jobs`, {
      periodFrom,
      periodTo,
      entityTypes,
    });
  }

  cancelJob(id: string): Observable<ReviewJobResponse> {
    return this.http.post<ReviewJobResponse>(`${this.base}/review-jobs/${id}/cancel`, null);
  }
}
