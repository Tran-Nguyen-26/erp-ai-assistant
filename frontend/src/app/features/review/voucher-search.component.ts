import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { VoucherApiService } from '../../core/api/voucher-api.service';
import { ViolationApiService } from '../../core/api/violation-api.service';
import { errorMessage } from '../../core/http-error';
import { PageResponse } from '../../core/models/page.model';
import { VoucherListItem } from '../../core/models/voucher.model';
import { VOUCHER_TYPES } from '../../core/models/voucher-types';
import { SeverityChipComponent } from '../../shared/severity-chip.component';

function isoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

@Component({
  selector: 'app-voucher-search',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    SeverityChipComponent,
  ],
  template: `
    <div class="page">
      <h1>Tìm chứng từ ERP</h1>
      <div class="card toolbar-row">
        <mat-form-field appearance="outline">
          <mat-label>Loại chứng từ</mat-label>
          <mat-select [(ngModel)]="voucherType">
            @for (t of voucherTypes; track t.value) {
              <mat-option [value]="t.value">{{ t.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Từ ngày</mat-label>
          <input matInput type="date" [(ngModel)]="periodFrom" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Đến ngày</mat-label>
          <input matInput type="date" [(ngModel)]="periodTo" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Số chứng từ / diễn giải / đối tượng</mat-label>
          <input matInput [(ngModel)]="q" (keyup.enter)="search(0)" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>MST đối tượng</mat-label>
          <input matInput [(ngModel)]="partnerTaxCode" (keyup.enter)="search(0)" />
        </mat-form-field>
        <mat-checkbox [(ngModel)]="hasViolations">Chỉ chứng từ có vi phạm</mat-checkbox>
        <button mat-flat-button (click)="search(0)" [disabled]="loading()">Tìm</button>
      </div>

      @if (message()) {
        <p class="muted">{{ message() }}</p>
      }

      <table class="full">
        <thead>
          <tr>
            <th>Số chứng từ</th>
            <th>Ngày</th>
            <th>Đối tượng</th>
            <th>Diễn giải</th>
            <th class="num">Tổng tiền</th>
            <th>Vi phạm</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (v of data()?.items ?? []; track v.entityRef) {
            <tr>
              <td>{{ v.voucherNo ?? v.entityRef }}</td>
              <td>{{ v.voucherDate }}</td>
              <td>
                {{ v.partner?.name }}<br /><span class="muted">{{ v.partner?.taxCode }}</span>
              </td>
              <td>{{ v.description }}</td>
              <td class="num">{{ v.totalAmount }} {{ v.currency }}</td>
              <td>
                @if (v.openViolationCount > 0 && v.maxSeverity) {
                  <app-severity-chip [severity]="v.maxSeverity" /> {{ v.openViolationCount }}
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="actions">
                <button mat-button (click)="check(v)" [disabled]="checking() === v.entityRef">
                  Rà soát
                </button>
                @if (v.openViolationCount > 0) {
                  <button mat-button (click)="viewViolations(v)">Xem vi phạm</button>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="7" class="muted">Không có chứng từ nào.</td>
            </tr>
          }
        </tbody>
      </table>
      <mat-paginator
        [length]="data()?.totalItems ?? 0"
        [pageSize]="20"
        [pageIndex]="data()?.page ?? 0"
        (page)="onPage($event)"
        hidePageSize
      />
    </div>
  `,
  styles: [
    `
      table {
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: 8px 12px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        vertical-align: top;
      }
      .num {
        text-align: right;
        white-space: nowrap;
      }
      .actions {
        white-space: nowrap;
      }
    `,
  ],
})
export class VoucherSearchComponent {
  private readonly vouchers = inject(VoucherApiService);
  private readonly violations = inject(ViolationApiService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly voucherTypes = VOUCHER_TYPES;
  protected voucherType = 'PURCHASE_INVOICE';
  protected periodFrom = isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  protected periodTo = isoDate(new Date());
  protected q = '';
  protected partnerTaxCode = '';
  protected hasViolations = false;

  protected readonly data = signal<PageResponse<VoucherListItem> | null>(null);
  protected readonly loading = signal(false);
  protected readonly checking = signal<string | null>(null);
  protected readonly message = signal('');

  protected search(page: number): void {
    if (!this.periodFrom || !this.periodTo || this.periodFrom > this.periodTo) {
      this.message.set('Khoảng ngày không hợp lệ.');
      return;
    }
    this.message.set('');
    this.loading.set(true);
    this.vouchers
      .search({
        voucherType: this.voucherType,
        periodFrom: this.periodFrom,
        periodTo: this.periodTo,
        q: this.q,
        partnerTaxCode: this.partnerTaxCode,
        hasViolations: this.hasViolations,
        page,
      })
      .subscribe({
        next: (d) => {
          this.loading.set(false);
          this.data.set(d);
        },
        error: (e) => {
          this.loading.set(false);
          this.message.set(errorMessage(e));
        },
      });
  }

  protected onPage(e: PageEvent): void {
    this.search(e.pageIndex);
  }

  protected check(v: VoucherListItem): void {
    this.checking.set(v.entityRef);
    this.violations.check(v.entityRef, v.entityType).subscribe({
      next: (r) => {
        this.checking.set(null);
        this.snack.open(`${r.violations.length} vi phạm sau ${r.checkedRules} rule`, 'Đóng', {
          duration: 4000,
        });
        this.search(this.data()?.page ?? 0);
      },
      error: (e) => {
        this.checking.set(null);
        this.snack.open(errorMessage(e), 'Đóng', { duration: 5000 });
      },
    });
  }

  protected viewViolations(v: VoucherListItem): void {
    void this.router.navigate(['/review'], { queryParams: { entityRef: v.entityRef } });
  }
}
