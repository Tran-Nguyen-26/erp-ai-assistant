import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProposalApiService } from '../../core/api/proposal-api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';
import { ProposalResponse } from '../../core/models/violation.model';

@Component({
  selector: 'app-proposal-list',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  template: `
    <div class="page">
      <h1>Duyệt đề xuất</h1>
      <div class="toolbar-row">
        <mat-form-field appearance="outline"
          ><mat-label>Trạng thái</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="load()">
            <mat-option value="">Tất cả</mat-option>
            @for (s of ['PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED', 'EXPORTED']; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select></mat-form-field
        >
        @if (auth.hasRole('CHIEF_ACCOUNTANT', 'ADMIN')) {
          <button mat-stroked-button (click)="export()" [disabled]="!selected().size">
            Xuất phiếu đề nghị (CSV) — {{ selected().size }}
          </button>
        }
      </div>
      <table class="full">
        <thead>
          <tr>
            <th></th>
            <th>Chứng từ</th>
            <th>Thay đổi</th>
            <th>Nguồn</th>
            <th>Trạng thái</th>
            <th>Tạo lúc</th>
          </tr>
        </thead>
        <tbody>
          @for (p of items(); track p.id) {
            <tr class="clickable" (click)="open(p)">
              <td (click)="$event.stopPropagation()">
                @if (p.status === 'APPROVED') {
                  <mat-checkbox [checked]="selected().has(p.id)" (change)="toggle(p.id)" />
                }
              </td>
              <td>{{ p.entityNo ?? p.entityRef }}</td>
              <td>
                @for (c of p.changes; track c.path) {
                  <div>
                    <code>{{ c.path }}</code>: {{ c.from }} → {{ c.to }}
                  </div>
                }
              </td>
              <td>{{ p.createdByType === 'AGENT' ? 'AI' : 'Thủ công' }}</td>
              <td>{{ p.status }}</td>
              <td>{{ p.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="muted">Không có đề xuất.</td>
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
export class ProposalListComponent implements OnInit {
  private readonly api = inject(ProposalApiService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly items = signal<ProposalResponse[]>([]);
  protected readonly selected = signal<Set<string>>(new Set());
  protected status = 'PENDING';

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.selected.set(new Set());
    this.api
      .list(this.status || undefined)
      .subscribe({ next: (p) => this.items.set(p.items), error: (e) => this.toast(e) });
  }

  protected toggle(id: string): void {
    const s = new Set(this.selected());
    if (s.has(id)) {
      s.delete(id);
    } else {
      s.add(id);
    }
    this.selected.set(s);
  }

  protected open(p: ProposalResponse): void {
    void this.router.navigate(['/approval', p.id]);
  }

  protected export(): void {
    this.api.export([...this.selected()]).subscribe({
      next: (res) => {
        const url = URL.createObjectURL(res.body as Blob);
        const a = document.createElement('a');
        a.href = url;
        a.download =
          /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ??
          'phieu-de-nghi.csv';
        a.click();
        URL.revokeObjectURL(url);
        this.load();
      },
      error: (e) => this.toast(e),
    });
  }

  private toast(e: unknown): void {
    this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 });
  }
}
