import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EntryDraftResponse, ExtractedInvoice, SuggestedEntry } from '../models/entry.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class EntryApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/entry/drafts';

  list(page = 0): Observable<PageResponse<EntryDraftResponse>> {
    return this.http.get<PageResponse<EntryDraftResponse>>(this.base, {
      params: new HttpParams().set('page', page),
    });
  }

  upload(file: File): Observable<EntryDraftResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<EntryDraftResponse>(this.base, form);
  }

  /** Nhập từ mô tả bằng lời / văn bản dán vào (LLM local). */
  createFromText(sourceText: string): Observable<EntryDraftResponse> {
    return this.http.post<EntryDraftResponse>(this.base, { sourceText });
  }

  get(id: string): Observable<EntryDraftResponse> {
    return this.http.get<EntryDraftResponse>(`${this.base}/${id}`);
  }

  update(
    id: string,
    invoice: ExtractedInvoice,
    suggestedEntries: SuggestedEntry[],
  ): Observable<EntryDraftResponse> {
    return this.http.put<EntryDraftResponse>(`${this.base}/${id}`, { invoice, suggestedEntries });
  }

  validate(id: string): Observable<EntryDraftResponse> {
    return this.http.post<EntryDraftResponse>(`${this.base}/${id}/validate`, null);
  }

  confirm(id: string): Observable<EntryDraftResponse> {
    return this.http.post<EntryDraftResponse>(`${this.base}/${id}/confirm`, null);
  }

  discard(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
