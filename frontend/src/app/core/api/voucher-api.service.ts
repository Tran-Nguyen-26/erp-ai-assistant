import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/page.model';
import { VoucherListItem } from '../models/voucher.model';

export interface VoucherQuery {
  voucherType: string;
  periodFrom: string;
  periodTo: string;
  q?: string;
  partnerTaxCode?: string;
  hasViolations?: boolean;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class VoucherApiService {
  private readonly http = inject(HttpClient);

  search(q: VoucherQuery): Observable<PageResponse<VoucherListItem>> {
    let params = new HttpParams()
      .set('voucherType', q.voucherType)
      .set('periodFrom', q.periodFrom)
      .set('periodTo', q.periodTo)
      .set('page', q.page ?? 0)
      .set('size', q.size ?? 20);
    if (q.q?.trim()) {
      params = params.set('q', q.q.trim());
    }
    if (q.partnerTaxCode?.trim()) {
      params = params.set('partnerTaxCode', q.partnerTaxCode.trim());
    }
    if (q.hasViolations) {
      params = params.set('hasViolations', true);
    }
    return this.http.get<PageResponse<VoucherListItem>>('/api/v1/erp/vouchers', { params });
  }
}
