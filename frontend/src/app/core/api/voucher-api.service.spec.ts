import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { VoucherApiService } from './voucher-api.service';

describe('VoucherApiService', () => {
  let api: VoucherApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(VoucherApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the filters that are set', () => {
    api
      .search({ voucherType: 'PAYMENT', periodFrom: '2026-10-01', periodTo: '2026-10-31', q: '  ' })
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/erp/vouchers');
    expect(req.request.params.get('voucherType')).toBe('PAYMENT');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('q')).toBeFalse();
    expect(req.request.params.has('hasViolations')).toBeFalse();
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('trims q, forwards the tax code and the violation filter', () => {
    api
      .search({
        voucherType: 'BANK_DEBIT',
        periodFrom: '2026-10-01',
        periodTo: '2026-10-31',
        q: ' MH01 ',
        partnerTaxCode: '0101234567',
        hasViolations: true,
        page: 2,
      })
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/erp/vouchers');
    expect(req.request.params.get('q')).toBe('MH01');
    expect(req.request.params.get('partnerTaxCode')).toBe('0101234567');
    expect(req.request.params.get('hasViolations')).toBe('true');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 0 });
  });
});
