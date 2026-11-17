import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ViolationApiService } from '../../core/api/violation-api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';
import { ExplanationResponse, ViolationResponse } from '../../core/models/violation.model';
import { SeverityChipComponent } from '../../shared/severity-chip.component';

@Component({
  selector: 'app-violation-detail',
  standalone: true,
  imports: [
    FormsModule,
    JsonPipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    SeverityChipComponent,
  ],
  template: `
    <div class="page">
      <a routerLink="/review" class="muted">← Danh sách</a>
      @if (v(); as v) {
        <h1><app-severity-chip [severity]="v.severity" /> {{ v.ruleName }}</h1>
        <div class="card">
          <dl class="kv">
            <dt>Rule</dt>
            <dd>{{ v.ruleId }}</dd>
            <dt>Chứng từ</dt>
            <dd>
              {{ v.entityNo ?? '—' }} <span class="muted">({{ v.entityRef }})</span>
            </dd>
            <dt>Ngày chứng từ</dt>
            <dd>{{ v.entityDate ?? '—' }}</dd>
            <dt>Trạng thái</dt>
            <dd>{{ v.status }}</dd>
            <dt>Thông điệp</dt>
            <dd>{{ v.message }}</dd>
            @if (v.dismissReason) {
              <dt>Lý do bỏ qua</dt>
              <dd>{{ v.dismissReason }}</dd>
            }
          </dl>
          <h3>Bằng chứng</h3>
          <pre class="evidence">{{ v.evidence | json }}</pre>
          <div class="toolbar-row">
            <button mat-flat-button (click)="explain(false)" [disabled]="busy()">
              Giải thích bằng AI
            </button>
            @if (exp()) {
              <button mat-button (click)="explain(true)" [disabled]="busy()">Tạo lại</button>
            }
            @if (v.status === 'DISMISSED' || v.status === 'RESOLVED') {
              <button mat-button (click)="reopen()">Mở lại</button>
            } @else {
              <button mat-button (click)="dismiss()">Bỏ qua…</button>
            }
          </div>
          @if (busy()) {
            <mat-progress-bar mode="indeterminate" />
          }
        </div>

        @if (exp(); as e) {
          <div class="card">
            <h2>
              Giải thích <span class="chip INFO">{{ e.explanation.supportStatus }}</span>
              @if (e.explanation.cached) {
                <span class="muted">(từ bộ nhớ đệm)</span>
              }
            </h2>
            <p>
              <strong>{{ e.explanation.summary }}</strong>
            </p>
            <p style="white-space: pre-wrap">{{ e.explanation.explanation }}</p>
            <p class="muted">
              Độ tin cậy {{ e.explanation.confidence }} · {{ e.explanation.model }} ·
              {{ e.explanation.promptVersion }}
            </p>
            @if (e.explanation.citations.length) {
              <h3>Trích dẫn</h3>
              @for (c of e.explanation.citations; track c.index) {
                <blockquote>
                  <strong>[{{ c.index }}] {{ c.docNumber }}</strong> — {{ c.headingPath }}<br />“{{
                    c.quote
                  }}”
                </blockquote>
              }
            }
            @if (e.proposal) {
              <p>
                Đã tạo đề xuất sửa:
                <a [routerLink]="['/approval', e.proposal.id]"
                  >xem đề xuất ({{ e.proposal.status }})</a
                >
              </p>
            } @else if (e.proposalSkippedReason) {
              <p class="muted">Không tạo đề xuất: {{ e.proposalSkippedReason }}</p>
            }
          </div>
        }

        @if (auth.hasRole('CHIEF_ACCOUNTANT', 'ADMIN')) {
          <div class="card">
            <h3>Tạo đề xuất thủ công</h3>
            <div class="toolbar-row">
              <mat-form-field appearance="outline"
                ><mat-label>Đường dẫn</mat-label>
                <input matInput [(ngModel)]="path" placeholder="partner.taxCode"
              /></mat-form-field>
              <mat-form-field appearance="outline"
                ><mat-label>Giá trị mới</mat-label> <input matInput [(ngModel)]="to"
              /></mat-form-field>
              <mat-form-field appearance="outline" class="grow"
                ><mat-label>Lý do</mat-label> <input matInput [(ngModel)]="rationale"
              /></mat-form-field>
              <button
                mat-stroked-button
                (click)="propose()"
                [disabled]="!path || !to || !rationale"
              >
                Tạo & kiểm chứng
              </button>
            </div>
            <p class="muted">
              Đề xuất sẽ được chạy lại qua rule; không qua kiểm chứng sẽ không được lưu.
            </p>
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      blockquote {
        margin: 8px 0;
        padding-left: 12px;
        border-left: 3px solid var(--mat-sys-primary);
      }
    `,
  ],
})
export class ViolationDetailComponent implements OnInit {
  private readonly api = inject(ViolationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly v = signal<ViolationResponse | null>(null);
  protected readonly exp = signal<ExplanationResponse | null>(null);
  protected readonly busy = signal(false);
  protected path = '';
  protected to = '';
  protected rationale = '';

  private get id(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.get(this.id).subscribe({ next: (v) => this.v.set(v), error: (e) => this.toast(e) });
  }

  protected explain(regenerate: boolean): void {
    this.busy.set(true);
    this.api.explain(this.id, regenerate).subscribe({
      next: (r) => {
        this.exp.set(r);
        this.busy.set(false);
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast(e);
      },
    });
  }

  protected dismiss(): void {
    const reason = window.prompt('Lý do bỏ qua violation này:');
    if (!reason?.trim()) {
      return;
    }
    this.api
      .dismiss(this.id, reason)
      .subscribe({ next: (v) => this.v.set(v), error: (e) => this.toast(e) });
  }

  protected reopen(): void {
    this.api.reopen(this.id).subscribe({ next: (v) => this.v.set(v), error: (e) => this.toast(e) });
  }

  protected propose(): void {
    this.api
      .createProposal(this.id, this.path.trim(), this.to.trim(), this.rationale.trim())
      .subscribe({
        next: (p) => void this.router.navigate(['/approval', p.id]),
        error: (e) => this.toast(e),
      });
  }

  private toast(e: unknown): void {
    this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 });
  }
}
