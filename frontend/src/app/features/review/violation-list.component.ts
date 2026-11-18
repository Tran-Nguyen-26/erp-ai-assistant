import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ViolationApiService } from '../../core/api/violation-api.service';
import { errorMessage } from '../../core/http-error';
import { PageResponse } from '../../core/models/page.model';
import { ViolationResponse, ViolationSummary } from '../../core/models/violation.model';
import { SeverityChipComponent } from '../../shared/severity-chip.component';
import { VOUCHER_TYPES } from '../../core/models/voucher-types';

@Component({
  selector: 'app-violation-list',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    SeverityChipComponent,
  ],
  template: `
    <div class="page">
      <h1>Rà soát chứng từ</h1>

      @if (summary(); as s) {
        <div class="card toolbar-row">
          <strong>Tổng: {{ s.total }}</strong>
          @for (e of entries(s.bySeverity); track e[0]) {
            <span><app-severity-chip [severity]="e[0]" /> {{ e[1] }}</span>
          }
          <span class="muted"
            >· Mở: {{ s.byStatus['OPEN'] ?? 0 }} · Đã giải thích:
            {{ s.byStatus['EXPLAINED'] ?? 0 }} · Đề xuất: {{ s.byStatus['PROPOSED'] ?? 0 }}</span
          >
        </div>
      }

      <div class="card">
        <div class="toolbar-row">
          <mat-form-field appearance="outline">
            <mat-label>Loại chứng từ</mat-label>
            <mat-select [(ngModel)]="checkType">
              @for (t of voucherTypes; track t.value) {
                <mat-option [value]="t.value">{{ t.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="grow">
            <mat-label>Mã chứng từ ERP (GUID) cần rà soát ngay</mat-label>
            <input matInput [(ngModel)]="checkRef" placeholder="vd: 3F2A…" />
          </mat-form-field>
          <button mat-flat-button (click)="check()" [disabled]="!checkRef.trim() || checking()">
            Rà soát
          </button>
        </div>
        @if (checkMsg()) {
          <p class="muted">{{ checkMsg() }}</p>
        }
      </div>

      <div class="toolbar-row">
        <mat-form-field appearance="outline">
          <mat-label>Trạng thái</mat-label>
          <mat-select multiple [(ngModel)]="status" (selectionChange)="reload(0)">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mức độ</mat-label>
          <mat-select [(ngModel)]="severity" (selectionChange)="reload(0)">
            <mat-option value="">Tất cả</mat-option>
            @for (s of ['ERROR', 'WARNING', 'INFO']; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      @if (entityRef) {
        <p class="muted">
          Đang lọc theo chứng từ {{ entityRef }}
          <button mat-button (click)="clearEntity()">Bỏ lọc</button>
        </p>
      }

      <table class="full">
        <thead>
          <tr>
            <th>Mức độ</th>
            <th>Rule</th>
            <th>Chứng từ</th>
            <th>Thông điệp</th>
            <th>Trạng thái</th>
            <th>Phát hiện</th>
          </tr>
        </thead>
        <tbody>
          @for (v of data()?.items ?? []; track v.id) {
            <tr class="clickable" (click)="open(v)">
              <td><app-severity-chip [severity]="v.severity" /></td>
              <td>{{ v.ruleId }}</td>
              <td>
                {{ v.entityNo ?? v.entityRef }}<br /><span class="muted">{{ v.entityDate }}</span>
              </td>
              <td>{{ v.message }}</td>
              <td>{{ v.status }}</td>
              <td>{{ v.detectedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="muted">Không có violation nào.</td>
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
    `,
  ],
})
export class ViolationListComponent implements OnInit {
  private readonly api = inject(ViolationApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);

  protected readonly statuses = [
    'OPEN',
    'EXPLAINED',
    'PROPOSED',
    'RESOLVED_PENDING_ERP',
    'RESOLVED',
    'DISMISSED',
  ];
  protected status: string[] = ['OPEN', 'EXPLAINED', 'PROPOSED'];
  protected severity = '';
  protected entityRef = '';
  protected checkRef = '';
  protected checkType = 'PURCHASE_INVOICE';
  protected readonly voucherTypes = VOUCHER_TYPES;

  protected readonly data = signal<PageResponse<ViolationResponse> | null>(null);
  protected readonly summary = signal<ViolationSummary | null>(null);
  protected readonly checking = signal(false);
  protected readonly checkMsg = signal('');

  ngOnInit(): void {
    this.entityRef = this.route.snapshot.queryParamMap.get('entityRef') ?? '';
    this.reload(0);
  }

  protected entries(r: Record<string, number>): [string, number][] {
    return Object.entries(r);
  }

  protected reload(page: number): void {
    this.api
      .list({
        status: this.status,
        severity: this.severity || undefined,
        entityRef: this.entityRef || undefined,
        page,
      })
      .subscribe({
        next: (d) => this.data.set(d),
        error: (e) => this.snack.open(errorMessage(e), 'Đóng', { duration: 5000 }),
      });
    this.api
      .summary()
      .subscribe({ next: (s) => this.summary.set(s), error: () => this.summary.set(null) });
  }

  protected clearEntity(): void {
    this.entityRef = '';
    void this.router.navigate([], { queryParams: {} });
    this.reload(0);
  }

  protected onPage(e: PageEvent): void {
    this.reload(e.pageIndex);
  }

  protected check(): void {
    this.checking.set(true);
    this.api.check(this.checkRef.trim(), this.checkType).subscribe({
      next: (r) => {
        this.checking.set(false);
        this.checkMsg.set(
          `Đã chạy ${r.checkedRules} rule trong ${r.durationMs} ms: ${r.violations.length} vi phạm.`,
        );
        this.reload(0);
      },
      error: (e) => {
        this.checking.set(false);
        this.checkMsg.set(errorMessage(e));
      },
    });
  }

  protected open(v: ViolationResponse): void {
    void this.router.navigate(['/review', v.id]);
  }
}
