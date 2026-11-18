import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from './core/auth.service';
import { Role } from './core/models/auth.model';

interface NavItem {
  label: string;
  path: string;
  roles?: Role[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    @if (auth.user(); as user) {
      <mat-toolbar color="primary" class="bar">
        <span class="brand">ERP AI Assistant</span>
        <nav>
          @for (item of nav(); track item.path) {
            <a mat-button [routerLink]="item.path" routerLinkActive="active">{{ item.label }}</a>
          }
        </nav>
        <span class="grow"></span>
        <span class="who">{{ user.fullName }} · {{ roleLabel(user.role) }}</span>
        <button mat-button (click)="auth.logout()"><mat-icon>logout</mat-icon> Đăng xuất</button>
      </mat-toolbar>
    }
    <router-outlet />
  `,
  styles: [
    `
      .bar {
        gap: 12px;
        flex-wrap: wrap;
      }
      .brand {
        font-weight: 600;
        margin-right: 12px;
      }
      nav a.active {
        background: rgba(255, 255, 255, 0.18);
      }
      .grow {
        flex: 1;
      }
      .who {
        font-size: 13px;
        opacity: 0.9;
      }
    `,
  ],
})
export class App {
  protected readonly auth = inject(AuthService);

  private readonly items: NavItem[] = [
    { label: 'Trợ lý', path: '/assistant' },
    { label: 'Nhập liệu', path: '/entry' },
    { label: 'Chứng từ', path: '/vouchers' },
    { label: 'Rà soát', path: '/review' },
    { label: 'Job hàng loạt', path: '/jobs' },
    { label: 'Duyệt đề xuất', path: '/approval' },
    { label: 'Tổng quan', path: '/admin/dashboard' },
    { label: 'Rule', path: '/admin/rules', roles: ['ADMIN'] },
    { label: 'Kho tri thức', path: '/admin/knowledge' },
    { label: 'Nhật ký', path: '/admin/audit', roles: ['CHIEF_ACCOUNTANT', 'ADMIN'] },
  ];

  protected readonly nav = computed(() => {
    const role = this.auth.user()?.role;
    return this.items.filter((i) => !i.roles || (role && i.roles.includes(role)));
  });

  protected roleLabel(role: Role): string {
    return { ACCOUNTANT: 'Kế toán viên', CHIEF_ACCOUNTANT: 'Kế toán trưởng', ADMIN: 'Quản trị' }[
      role
    ];
  }
}
