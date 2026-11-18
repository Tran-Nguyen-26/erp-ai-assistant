import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { VoucherSearchComponent } from './voucher-search.component';

const PAGE = {
  items: [
    {
      entityRef: 'ref-1',
      entityType: 'PURCHASE_INVOICE',
      voucherNo: 'MH001',
      voucherDate: '2026-10-15',
      description: 'Mua văn phòng phẩm',
      partner: { code: 'NCC1', name: 'Công ty Alpha', taxCode: '0101234567' },
      totalAmount: '1100000.00',
      currency: 'VND',
      openViolationCount: 2,
      maxSeverity: 'ERROR',
    },
    {
      entityRef: 'ref-2',
      entityType: 'PURCHASE_INVOICE',
      voucherNo: 'MH002',
      voucherDate: '2026-10-16',
      description: null,
      partner: null,
      totalAmount: '500000.00',
      currency: 'VND',
      openViolationCount: 0,
      maxSeverity: null,
    },
  ],
  page: 0,
  size: 20,
  totalItems: 2,
  totalPages: 1,
};

describe('VoucherSearchComponent', () => {
  let fixture: ComponentFixture<VoucherSearchComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(VoucherSearchComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function clickSearch(): void {
    const buttons = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.trim() === 'Tìm')!.click();
  }

  it('shows an empty state before searching and issues no request', () => {
    expect(fixture.nativeElement.textContent).toContain('Không có chứng từ nào');
    http.expectNone('/api/v1/erp/vouchers');
  });

  it('searches and shows violation counts only for vouchers that have them', () => {
    clickSearch();
    const req = http.expectOne((r) => r.url === '/api/v1/erp/vouchers');
    expect(req.request.params.get('voucherType')).toBe('PURCHASE_INVOICE');
    req.flush(PAGE);
    fixture.detectChanges();

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('MH001');
    expect(rows[0].textContent).toContain('Công ty Alpha');
    expect(rows[0].textContent).toContain('2');
    expect(rows[0].textContent).toContain('Xem vi phạm');
    expect(rows[1].textContent).toContain('—');
    expect(rows[1].textContent).not.toContain('Xem vi phạm');
  });

  it('blocks a search with an inverted period', () => {
    (fixture.componentInstance as unknown as { periodFrom: string }).periodFrom = '2026-12-31';
    (fixture.componentInstance as unknown as { periodTo: string }).periodTo = '2026-01-01';
    clickSearch();
    fixture.detectChanges();
    http.expectNone('/api/v1/erp/vouchers');
    expect(fixture.nativeElement.textContent).toContain('Khoảng ngày không hợp lệ');
  });

  it('shows the server error message when the search fails', () => {
    clickSearch();
    http
      .expectOne((r) => r.url === '/api/v1/erp/vouchers')
      .flush(
        { detail: 'ERP không khả dụng', code: 'ERP_UNAVAILABLE' },
        { status: 502, statusText: 'Bad Gateway' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ERP_UNAVAILABLE');
  });
});
