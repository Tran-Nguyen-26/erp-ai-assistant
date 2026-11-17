import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page.model';
import { ProposalResponse } from '../models/violation.model';

@Injectable({ providedIn: 'root' })
export class ProposalApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/proposals';

  list(status?: string, page = 0, size = 20): Observable<PageResponse<ProposalResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<ProposalResponse>>(this.base, { params });
  }

  get(id: string): Observable<ProposalResponse> {
    return this.http.get<ProposalResponse>(`${this.base}/${id}`);
  }

  approve(id: string, note: string): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>(`${this.base}/${id}/approve`, { note });
  }

  reject(id: string, note: string): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>(`${this.base}/${id}/reject`, { note });
  }

  /** Xuất phiếu đề nghị điều chỉnh (CSV). */
  export(ids: string[]): Observable<HttpResponse<Blob>> {
    return this.http.post(
      `${this.base}/export`,
      { proposalIds: ids, format: 'CSV' },
      { observe: 'response', responseType: 'blob' },
    );
  }
}
