import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/api/admin-api.service';
import { errorMessage } from '../../core/http-error';
import { RuleResponse } from '../../core/models/admin.model';
import { SeverityChipComponent } from '../../shared/severity-chip.component';

@Component({
  selector: 'app-rules',
  standalone: true,
  imports: [MatButtonModule, MatSlideToggleModule, SeverityChipComponent],
  template: `
    <div class="page">
      <h1>Quản lý rule</h1>
      <div class="toolbar-row">
        <button mat-stroked-button (click)="reload()">Nạp lại từ YAML</button>
      </div>
      <table class="full">
        <thead>
          <tr>
            <th>Bật</th>
            <th>Rule</th>
            <th>Mức độ</th>
            <th>Tham số</th>
            <th>Vi phạm mở</th>
            <th>v</th>
          </tr>
        </thead>
        <tbody>
          @for (r of rules(); track r.ruleId) {
            <tr>
              <td>
                <mat-slide-toggle [checked]="r.enabled" (change)="toggle(r, $event.checked)" />
              </td>
              <td>
                <strong>{{ r.ruleId }}</strong><br /><span class="muted">{{ r.name }}</span>
              </td>
              <td><app-severity-chip [severity]="r.severity" /></td>
              <td>
                <code>{{ params(r) }}</code>
              </td>
              <td>{{ r.openViolationCount }}</td>
              <td>{{ r.version }}</td>
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
export class RulesComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly rules = signal<RuleResponse[]>([]);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api
      .rules()
      .subscribe({ next: (r) => this.rules.set(r), error: (e) => this.toast(errorMessage(e)) });
  }

  protected params(r: RuleResponse): string {
    return JSON.stringify(r.params);
  }

  protected toggle(r: RuleResponse, enabled: boolean): void {
    this.api.patchRule(r.ruleId, { enabled }).subscribe({
      next: () => this.load(),
      error: (e) => {
        this.toast(errorMessage(e));
        this.load();
      },
    });
  }

  protected reload(): void {
    this.api.reloadRules().subscribe({
      next: (r) => {
        this.toast(
          `Thêm ${r.added.length}, cập nhật ${r.updated.length}, giữ nguyên ${r.unchanged}`,
        );
        this.load();
      },
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  private toast(m: string): void {
    this.snack.open(m, 'Đóng', { duration: 5000 });
  }
}
