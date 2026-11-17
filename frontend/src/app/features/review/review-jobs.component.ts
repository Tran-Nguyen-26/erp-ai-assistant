import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/api/admin-api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';
import { ReviewJobResponse } from '../../core/models/admin.model';

@Component({
  selector: 'app-review-jobs',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="page">
      <h1>Rà soát hàng loạt</h1>
      @if (auth.hasRole('CHIEF_ACCOUNTANT', 'ADMIN')) {
        <div class="card toolbar-row">
          <mat-form-field appearance="outline"
            ><mat-label>Từ ngày</mat-label> <input matInput type="date" [(ngModel)]="from"
          /></mat-form-field>
          <mat-form-field appearance="outline"
            ><mat-label>Đến ngày</mat-label> <input matInput type="date" [(ngModel)]="to"
          /></mat-form-field>
          <button mat-flat-button (click)="create()" [disabled]="!from || !to">Tạo job</button>
          <span class="muted">Tối đa 12 tháng · hóa đơn mua hàng</span>
        </div>
      }
      @for (j of jobs(); track j.id) {
        <div class="card">
          <div class="toolbar-row">
            <strong>{{ j.periodFrom }} → {{ j.periodTo }}</strong>
            <span class="chip INFO">{{ j.status }}</span>
            <span class="grow"></span>
            <span class="muted">{{ j.startedAt | date: 'dd/MM HH:mm' }}</span>
            @if (
              (j.status === 'PENDING' || j.status === 'RUNNING') &&
              auth.hasRole('CHIEF_ACCOUNTANT', 'ADMIN')
            ) {
              <button mat-button (click)="cancel(j)">Hủy</button>
            }
          </div>
          <mat-progress-bar mode="determinate" [value]="j.progress.percent" />
          <p class="muted">
            {{ j.progress.processedDocuments }}/{{ j.progress.totalDocuments }} chứng từ ({{
              j.progress.percent
            }}%) · {{ j.violationCount }} vi phạm · batch lỗi: {{ j.progress.failedBatches }}/{{
              j.totalBatches
            }}
          </p>
        </div>
      } @empty {
        <p class="muted">Chưa có job nào.</p>
      }
    </div>
  `,
})
export class ReviewJobsComponent implements OnInit, OnDestroy {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly jobs = signal<ReviewJobResponse[]>([]);
  protected from = '';
  protected to = '';
  private timer: ReturnType<typeof setInterval> | undefined;

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 3000); // theo dõi tiến độ (polling)
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  private load(): void {
    this.api.jobs().subscribe({ next: (p) => this.jobs.set(p.items), error: () => undefined });
  }

  protected create(): void {
    this.api.createJob(this.from, this.to).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 }),
    });
  }

  protected cancel(j: ReviewJobResponse): void {
    this.api.cancelJob(j.id).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(errorMessage(e), 'Đóng', { duration: 6000 }),
    });
  }
}
