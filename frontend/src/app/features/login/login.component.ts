import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `
    <div class="wrap">
      <form class="card box" (ngSubmit)="submit()">
        <h1>ERP AI Assistant</h1>
        <p class="muted">Rà soát & hỗ trợ nhập liệu kế toán</p>
        <mat-form-field appearance="outline">
          <mat-label>Tên đăng nhập</mat-label>
          <input matInput name="username" [(ngModel)]="username" autocomplete="username" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mật khẩu</mat-label>
          <input
            matInput
            type="password"
            name="password"
            [(ngModel)]="password"
            autocomplete="current-password"
            required
          />
        </mat-form-field>
        @if (error()) {
          <p class="err" role="alert">{{ error() }}</p>
        }
        @if (busy()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <button mat-flat-button type="submit" [disabled]="busy() || !username || !password">
          Đăng nhập
        </button>
      </form>
    </div>
  `,
  styles: [
    `
      .wrap {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 16px;
      }
      .box {
        width: min(380px, 100%);
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      h1 {
        margin: 0;
        font: var(--mat-sys-headline-small);
      }
      .err {
        color: var(--mat-sys-error);
        margin: 0 0 8px;
      }
    `,
  ],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected username = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected submit(): void {
    this.busy.set(true);
    this.error.set('');
    this.auth.login(this.username, this.password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
