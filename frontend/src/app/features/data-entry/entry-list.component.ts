import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EntryApiService } from '../../core/api/entry-api.service';
import { errorMessage } from '../../core/http-error';
import { EntryDraftResponse } from '../../core/models/entry.model';

@Component({
  selector: 'app-entry-list',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="page">
      <h1>Nhập liệu từ hóa đơn điện tử</h1>
      <div class="card toolbar-row">
        <input #file type="file" accept=".xml" hidden (change)="upload($event)" />
        <button mat-flat-button (click)="file.click()" [disabled]="busy()">
          Tải lên hóa đơn XML
        </button>
        <span class="muted"
          >Hệ thống tự điền form, gợi ý định khoản và kiểm tra rule (gồm trùng hóa đơn) trước khi
          lưu.</span
        >
      </div>
      @if (busy()) {
        <mat-progress-bar mode="indeterminate" />
      }
      <table class="full">
        <thead>
          <tr>
            <th>Hóa đơn</th>
            <th>Người bán</th>
            <th>Tổng thanh toán</th>
            <th>Cảnh báo</th>
            <th>Trạng thái</th>
            <th>Tạo lúc</th>
          </tr>
        </thead>
        <tbody>
          @for (d of items(); track d.id) {
            <tr class="clickable" (click)="open(d)">
              <td>
                {{ d.invoice.invoiceSeries }}-{{ d.invoice.invoiceNo }}<br /><span class="muted">{{
                  d.invoice.invoiceDate
                }}</span>
              </td>
              <td>
                {{ d.invoice.sellerName }}<br /><span class="muted">{{
                  d.invoice.sellerTaxCode
                }}</span>
              </td>
              <td>{{ d.invoice.totalPayable }}</td>
              <td>{{ d.validation.length }}</td>
              <td>{{ d.status }}</td>
              <td>{{ d.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="muted">Chưa có nháp nào.</td>
            </tr>
          }
        </tbody>
      </table>
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
export class EntryListComponent implements OnInit {
  private readonly api = inject(EntryApiService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly items = signal<EntryDraftResponse[]>([]);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.api
      .list()
      .subscribe({ next: (p) => this.items.set(p.items), error: (e) => this.toast(e) });
  }

  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.busy.set(true);
    this.api.upload(file).subscribe({
      next: (d) => void this.router.navigate(['/entry', d.id]),
      error: (e) => {
        this.busy.set(false);
        this.toast(e);
      },
    });
    input.value = '';
  }

  protected open(d: EntryDraftResponse): void {
    void this.router.navigate(['/entry', d.id]);
  }

  private toast(e: unknown): void {
    this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 });
  }
}
