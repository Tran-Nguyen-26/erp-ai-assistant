import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProposalApiService } from '../../core/api/proposal-api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';
import { ProposalResponse } from '../../core/models/violation.model';

@Component({
  selector: 'app-proposal-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="page">
      <a routerLink="/approval" class="muted">← Danh sách</a>
      @if (p(); as p) {
        <h1>
          Đề xuất sửa — {{ p.entityNo ?? p.entityRef }}
          <span class="chip INFO">{{ p.status }}</span>
        </h1>
        <div class="card">
          <h3>So sánh trước / sau</h3>
          <table class="full">
            <thead>
              <tr>
                <th>Trường</th>
                <th>Trước</th>
                <th>Sau</th>
              </tr>
            </thead>
            <tbody>
              @for (c of p.changes; track c.path) {
                <tr>
                  <td>
                    <code>{{ c.path }}</code>
                  </td>
                  <td class="del">{{ c.from }}</td>
                  <td class="add">{{ c.to }}</td>
                </tr>
              }
            </tbody>
          </table>
          <p><strong>Lý do:</strong> {{ p.rationale }}</p>
          <p class="muted">
            Nguồn:
            {{ p.createdByType === 'AGENT' ? 'AI (đã kiểm chứng bằng rule)' : 'Người dùng' }} ·
            Rule: {{ p.violation.ruleId ?? '—' }}
          </p>
        </div>
        <div class="card">
          <h3>Kiểm chứng bằng rule: {{ p.verification.passed ? 'ĐẠT' : 'KHÔNG ĐẠT' }}</h3>
          <p class="muted">Đã chạy: {{ p.verification.rulesChecked.join(', ') }}</p>
        </div>
        @if (p.citations.length) {
          <div class="card">
            <h3>Căn cứ</h3>
            @for (c of p.citations; track c.index) {
              <p>[{{ c.index }}] {{ c.docNumber }} — {{ c.headingPath }}<br />“{{ c.quote }}”</p>
            }
          </div>
        }
        @if (p.status === 'PENDING' && auth.hasRole('CHIEF_ACCOUNTANT', 'ADMIN')) {
          <div class="card">
            <mat-form-field appearance="outline" class="full"
              ><mat-label>Ghi chú (bắt buộc khi từ chối)</mat-label>
              <textarea matInput rows="2" [(ngModel)]="note"></textarea>
            </mat-form-field>
            <div class="toolbar-row">
              <button mat-flat-button (click)="approve()">Chấp nhận</button>
              <button mat-stroked-button (click)="reject()" [disabled]="!note.trim()">
                Từ chối
              </button>
            </div>
            <p class="muted">
              Duyệt chỉ ghi nhận quyết định và xuất phiếu đề nghị; hệ thống không ghi vào ERP.
            </p>
          </div>
        } @else if (p.reviewNote) {
          <p><strong>Ghi chú duyệt:</strong> {{ p.reviewNote }}</p>
        }
      }
    </div>
  `,
  styles: [
    `
      .del {
        background: var(--mat-sys-error-container);
      }
      .add {
        background: #d8f3dc;
        color: #123;
      }
      td,
      th {
        padding: 6px 10px;
        text-align: left;
      }
      .full {
        width: 100%;
      }
    `,
  ],
})
export class ProposalDetailComponent implements OnInit {
  private readonly api = inject(ProposalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly p = signal<ProposalResponse | null>(null);
  protected note = '';

  private get id(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  ngOnInit(): void {
    this.api.get(this.id).subscribe({ next: (p) => this.p.set(p), error: (e) => this.toast(e) });
  }

  protected approve(): void {
    this.api
      .approve(this.id, this.note)
      .subscribe({ next: (p) => this.p.set(p), error: (e) => this.toast(e) });
  }
  protected reject(): void {
    this.api
      .reject(this.id, this.note)
      .subscribe({ next: (p) => this.p.set(p), error: (e) => this.toast(e) });
  }

  private toast(e: unknown): void {
    this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 });
  }
}
