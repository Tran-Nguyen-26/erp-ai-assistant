import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/api/admin-api.service';
import { errorMessage } from '../../core/http-error';
import { AuditLogResponse } from '../../core/models/admin.model';
import { PageResponse } from '../../core/models/page.model';

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    JsonPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
  ],
  template: `
    <div class="page">
      <h1>Nhật ký hoạt động</h1>
      <div class="toolbar-row">
        <mat-form-field appearance="outline"
          ><mat-label>Hành động</mat-label>
          <input
            matInput
            [(ngModel)]="action"
            placeholder="PROPOSAL_APPROVED"
            (keyup.enter)="load(0)"
        /></mat-form-field>
        <button mat-stroked-button (click)="load(0)">Lọc</button>
      </div>
      <table class="full">
        <thead>
          <tr>
            <th>Thời điểm</th>
            <th>Tác nhân</th>
            <th>Hành động</th>
            <th>Đối tượng</th>
            <th>Chi tiết</th>
          </tr>
        </thead>
        <tbody>
          @for (l of data()?.items ?? []; track l.id) {
            <tr>
              <td>{{ l.occurredAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
              <td>{{ l.actorType }}{{ l.actor?.username ? ' · ' + l.actor?.username : '' }}</td>
              <td>{{ l.action }}</td>
              <td>
                {{ l.targetType }} <span class="muted">{{ l.targetId }}</span>
              </td>
              <td>
                <code>{{ l.detail | json }}</code>
              </td>
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
export class AuditLogsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly data = signal<PageResponse<AuditLogResponse> | null>(null);
  protected action = '';

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.api.auditLogs(this.action.trim(), page).subscribe({
      next: (d) => this.data.set(d),
      error: (e) => this.snack.open(errorMessage(e), 'Đóng', { duration: 5000 }),
    });
  }

  protected onPage(e: PageEvent): void {
    this.load(e.pageIndex);
  }
}
