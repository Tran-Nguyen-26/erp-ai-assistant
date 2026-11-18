import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page.model';
import {
  CheckResultResponse,
  ExplanationResponse,
  ProposalResponse,
  ViolationResponse,
  ViolationSummary,
} from '../models/violation.model';

export interface ViolationQuery {
  status?: string[];
  severity?: string;
  ruleId?: string;
  entityRef?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ViolationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  list(q: ViolationQuery): Observable<PageResponse<ViolationResponse>> {
    let params = new HttpParams().set('page', q.page ?? 0).set('size', q.size ?? 20);
    (q.status ?? []).forEach((s) => (params = params.append('status', s)));
    if (q.severity) {
      params = params.set('severity', q.severity);
    }
    if (q.ruleId) {
      params = params.set('ruleId', q.ruleId);
    }
    if (q.entityRef) {
      params = params.set('entityRef', q.entityRef);
    }
    return this.http.get<PageResponse<ViolationResponse>>(`${this.base}/violations`, { params });
  }

  summary(): Observable<ViolationSummary> {
    return this.http.get<ViolationSummary>(`${this.base}/violations/summary`);
  }

  get(id: string): Observable<ViolationResponse> {
    return this.http.get<ViolationResponse>(`${this.base}/violations/${id}`);
  }

  check(entityRef: string, entityType = 'PURCHASE_INVOICE'): Observable<CheckResultResponse> {
    return this.http.post<CheckResultResponse>(`${this.base}/reviews/check`, {
      entityType: 'PURCHASE_INVOICE',
      entityRef,
    });
  }

  explain(id: string, regenerate = false): Observable<ExplanationResponse> {
    return this.http.post<ExplanationResponse>(`${this.base}/violations/${id}/explain`, null, {
      params: new HttpParams().set('regenerate', regenerate),
    });
  }

  dismiss(id: string, reason: string): Observable<ViolationResponse> {
    return this.http.post<ViolationResponse>(`${this.base}/violations/${id}/dismiss`, { reason });
  }

  reopen(id: string): Observable<ViolationResponse> {
    return this.http.post<ViolationResponse>(`${this.base}/violations/${id}/reopen`, null);
  }

  createProposal(
    violationId: string,
    path: string,
    to: string,
    rationale: string,
  ): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>(`${this.base}/violations/${violationId}/proposals`, {
      actionType: 'UPDATE_FIELD',
      changes: [{ path, to }],
      rationale,
    });
  }
}
