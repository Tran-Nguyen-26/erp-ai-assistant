import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EntryApiService } from '../../core/api/entry-api.service';
import { errorMessage } from '../../core/http-error';
import { EntryDraftResponse } from '../../core/models/entry.model';
import { SeverityChipComponent } from '../../shared/severity-chip.component';

/** Các trường cho phép sửa trực tiếp trên form. */
const EDITABLE: { key: keyof EntryDraftResponse['invoice']; label: string; type?: string }[] = [
  { key: 'invoiceTemplate', label: 'Mẫu số' },
  { key: 'invoiceSeries', label: 'Ký hiệu' },
  { key: 'invoiceNo', label: 'Số hóa đơn' },
  { key: 'invoiceDate', label: 'Ngày hóa đơn', type: 'date' },
  { key: 'sellerName', label: 'Người bán' },
  { key: 'sellerTaxCode', label: 'MST người bán' },
];

@Component({
  selector: 'app-entry-detail',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    SeverityChipComponent,
  ],
  template: `
    <div class="page">
      <a routerLink="/entry" class="muted">← Danh sách</a>
      @if (d(); as d) {
        <h1>
          Hóa đơn {{ d.invoice.invoiceSeries }}-{{ d.invoice.invoiceNo }}
          <span class="chip INFO">{{ d.status }}</span>
        </h1>

        <div class="card">
          <h3>Thông tin trích xuất</h3>
          <div class="toolbar-row">
            @for (f of fields; track f.key) {
              <mat-form-field appearance="outline">
                <mat-label
                  >{{ f.label
                  }}{{
                    lowConf(d, f.key) ? ' ⚠ cần kiểm tra (' + pct(d, f.key) + ')' : ''
                  }}</mat-label
                >
                <input
                  matInput
                  [type]="f.type ?? 'text'"
                  [ngModel]="value(f.key)"
                  (ngModelChange)="set(f.key, $event)"
                  [disabled]="locked(d)"
                />
              </mat-form-field>
            }
          </div>
          @if (d.partnerMatch.found === true) {
            <p>
              ✔ Nhà cung cấp có trong ERP: <strong>{{ d.partnerMatch.code }}</strong> —
              {{ d.partnerMatch.name }}
            </p>
          } @else if (d.partnerMatch.found === false) {
            <p class="warn">
              Nhà cung cấp chưa có trong ERP (MST {{ d.invoice.sellerTaxCode }}) — cần tạo mới trước
              khi nhập.
            </p>
          } @else {
            <p class="muted">Chưa đối chiếu được nhà cung cấp với ERP.</p>
          }
          <p>
            Tiền hàng <strong>{{ d.invoice.totalBeforeTax }}</strong> · Thuế
            <strong>{{ d.invoice.totalVat }}</strong> · Thanh toán
            <strong>{{ d.invoice.totalPayable }}</strong> {{ d.invoice.currency }}
          </p>
        </div>

        <div class="card">
          <h3>Dòng hàng & gợi ý định khoản</h3>
          <table class="full">
            <thead>
              <tr>
                <th>#</th>
                <th>Diễn giải</th>
                <th>Thành tiền</th>
                <th>Thuế suất</th>
                <th>Nợ</th>
                <th>Có</th>
                <th>Tin cậy</th>
              </tr>
            </thead>
            <tbody>
              @for (l of d.invoice.lines; track l.lineNo; let i = $index) {
                <tr>
                  <td>{{ l.lineNo }}</td>
                  <td>{{ l.description }}</td>
                  <td>{{ l.amount }}</td>
                  <td>{{ l.vatLabel ?? l.vatRate }}</td>
                  <td>
                    <input
                      class="acct"
                      [(ngModel)]="entries()[i].debitAccount"
                      [disabled]="locked(d)"
                    />
                  </td>
                  <td>
                    <input
                      class="acct"
                      [(ngModel)]="entries()[i].creditAccount"
                      [disabled]="locked(d)"
                    />
                  </td>
                  <td>{{ entries()[i].confidence }}</td>
                </tr>
              }
              @for (e of vatEntries(); track $index) {
                <tr>
                  <td></td>
                  <td>{{ e.reason }}</td>
                  <td>{{ e.amount }}</td>
                  <td></td>
                  <td>
                    <input class="acct" [(ngModel)]="e.debitAccount" [disabled]="locked(d)" />
                  </td>
                  <td>
                    <input class="acct" [(ngModel)]="e.creditAccount" [disabled]="locked(d)" />
                  </td>
                  <td>{{ e.confidence }}</td>
                </tr>
              }
            </tbody>
          </table>
          <p class="muted">
            Định khoản chỉ là gợi ý mặc định (độ tin cậy thấp) — kế toán phải xác nhận.
          </p>
        </div>

        <div class="card">
          <h3>Kết quả kiểm tra rule</h3>
          @for (v of d.validation; track $index) {
            <p>
              <app-severity-chip [severity]="v.severity" /> <strong>{{ v.ruleId }}</strong> —
              {{ v.message }}
            </p>
          } @empty {
            <p>✔ Không có cảnh báo.</p>
          }
        </div>

        @if (!locked(d)) {
          <div class="toolbar-row">
            <button mat-flat-button (click)="save()">Lưu & kiểm tra</button>
            <button mat-stroked-button (click)="validate()">Kiểm tra lại</button>
            <button mat-stroked-button (click)="confirm()" [disabled]="d.status !== 'VALIDATED'">
              Xác nhận
            </button>
            <span class="grow"></span>
            <button mat-button (click)="discard()">Xóa nháp</button>
          </div>
          <p class="muted">
            Xác nhận chỉ chốt dữ liệu để xuất/nhập thủ công; hệ thống không ghi vào ERP.
          </p>
        } @else {
          <button mat-stroked-button (click)="exportJson(d)">Xuất JSON</button>
        }
      }
    </div>
  `,
  styles: [
    `
      .acct {
        width: 90px;
        padding: 6px;
      }
      .warn {
        color: var(--mat-sys-error);
      }
      table {
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: 6px 10px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }
    `,
  ],
})
export class EntryDetailComponent implements OnInit {
  private readonly api = inject(EntryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly fields = EDITABLE;
  protected readonly d = signal<EntryDraftResponse | null>(null);
  protected readonly entries = signal<EntryDraftResponse['suggestedEntries']>([]);

  private get id(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  ngOnInit(): void {
    this.api
      .get(this.id)
      .subscribe({ next: (d) => this.apply(d), error: (e) => this.toast(errorMessage(e)) });
  }

  protected locked(d: EntryDraftResponse): boolean {
    return d.status === 'CONFIRMED' || d.status === 'DISCARDED';
  }

  protected lowConf(d: EntryDraftResponse, key: string): boolean {
    return d.lowConfidenceFields.includes(key);
  }

  protected pct(d: EntryDraftResponse, key: string): string {
    return `${Math.round((d.fieldConfidence[key] ?? 0) * 100)}%`;
  }

  protected value(key: keyof EntryDraftResponse['invoice']): string {
    return (this.d()?.invoice[key] as string | null) ?? '';
  }

  protected set(key: keyof EntryDraftResponse['invoice'], v: string): void {
    const cur = this.d();
    if (cur) {
      this.d.set({ ...cur, invoice: { ...cur.invoice, [key]: v || null } });
    }
  }

  /** Bút toán không gắn với dòng hàng (thuế GTGT). */
  protected vatEntries(): EntryDraftResponse['suggestedEntries'] {
    const n = this.d()?.invoice.lines.length ?? 0;
    return this.entries().slice(n);
  }

  protected save(): void {
    const cur = this.d();
    if (!cur) {
      return;
    }
    this.api
      .update(this.id, cur.invoice, this.entries())
      .subscribe({ next: (d) => this.apply(d), error: (e) => this.toast(errorMessage(e)) });
  }

  protected validate(): void {
    this.api
      .validate(this.id)
      .subscribe({ next: (d) => this.apply(d), error: (e) => this.toast(errorMessage(e)) });
  }

  protected confirm(): void {
    this.api.confirm(this.id).subscribe({
      next: (d) => this.apply(d),
      error: (e) => {
        this.toast(errorMessage(e));
        this.api.get(this.id).subscribe((d) => this.apply(d));
      },
    });
  }

  protected discard(): void {
    if (!window.confirm('Xóa nháp này?')) {
      return;
    }
    this.api.discard(this.id).subscribe({
      next: () => void this.router.navigate(['/entry']),
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  protected exportJson(d: EntryDraftResponse): void {
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(d, null, 2)], { type: 'application/json' }),
    );
    const a = document.createElement('a');
    a.href = url;
    a.download = `nhap-lieu-${d.invoice.invoiceSeries}-${d.invoice.invoiceNo}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private apply(d: EntryDraftResponse): void {
    this.d.set(d);
    this.entries.set(d.suggestedEntries.map((e) => ({ ...e })));
  }

  private toast(m: string): void {
    this.snack.open(m, 'Đóng', { duration: 6000 });
  }
}
