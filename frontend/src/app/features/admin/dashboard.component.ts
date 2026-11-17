import { Component, OnInit, inject, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/api/admin-api.service';
import { errorMessage } from '../../core/http-error';
import { DashboardStats } from '../../core/models/admin.model';
import { SeverityChipComponent } from '../../shared/severity-chip.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SeverityChipComponent],
  template: `
    <div class="page">
      <h1>Tổng quan</h1>
      @if (s(); as s) {
        <div class="grid">
          <div class="card">
            <div class="num">{{ s.violations.total }}</div>
            <div class="muted">Vi phạm</div>
            <div>Đang mở {{ s.violations.open }} · Đã xử lý {{ s.violations.resolved }}</div>
            <div>
              @for (e of sev(s); track e[0]) {
                <app-severity-chip [severity]="e[0]" /> {{ e[1] }} &nbsp;
              }
            </div>
          </div>
          <div class="card">
            <div class="num">{{ s.proposals.pending }}</div>
            <div class="muted">Đề xuất chờ duyệt</div>
            <div>Chấp nhận {{ s.proposals.approved }} · Từ chối {{ s.proposals.rejected }}</div>
            <div>Tỷ lệ chấp nhận {{ pct(s.proposals.acceptanceRate) }}</div>
          </div>
          <div class="card">
            <div class="num">{{ s.ai.explanations }}</div>
            <div class="muted">Giải thích AI</div>
            <div>Có căn cứ {{ pct(s.ai.supportedRate) }} · {{ s.ai.avgLatencyMs }} ms TB</div>
            <div>Phản hồi tích cực {{ pct(s.ai.positiveFeedbackRate) }}</div>
          </div>
        </div>
        <div class="card">
          <h3>Rule vi phạm nhiều nhất</h3>
          @for (r of s.topRules; track r.ruleId) {
            <div>
              {{ r.name }} <span class="muted">({{ r.ruleId }})</span> —
              <strong>{{ r.count }}</strong>
            </div>
          } @empty {
            <span class="muted">Chưa có dữ liệu.</span>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
        gap: 16px;
      }
      .num {
        font: var(--mat-sys-display-small);
      }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly s = signal<DashboardStats | null>(null);

  ngOnInit(): void {
    this.api.stats().subscribe({
      next: (s) => this.s.set(s),
      error: (e) => this.snack.open(errorMessage(e), 'Đóng', { duration: 5000 }),
    });
  }

  protected sev(s: DashboardStats): [string, number][] {
    return Object.entries(s.violations.bySeverity);
  }
  protected pct(v: number): string {
    return `${Math.round(v * 100)}%`;
  }
}
