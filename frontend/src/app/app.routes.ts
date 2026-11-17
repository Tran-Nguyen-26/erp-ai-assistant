import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'review' },
      {
        path: 'review',
        loadComponent: () =>
          import('./features/review/violation-list.component').then(
            (m) => m.ViolationListComponent,
          ),
      },
      {
        path: 'review/:id',
        loadComponent: () =>
          import('./features/review/violation-detail.component').then(
            (m) => m.ViolationDetailComponent,
          ),
      },
      {
        path: 'jobs',
        loadComponent: () =>
          import('./features/review/review-jobs.component').then((m) => m.ReviewJobsComponent),
      },
      {
        path: 'approval',
        loadComponent: () =>
          import('./features/approval/proposal-list.component').then(
            (m) => m.ProposalListComponent,
          ),
      },
      {
        path: 'approval/:id',
        loadComponent: () =>
          import('./features/approval/proposal-detail.component').then(
            (m) => m.ProposalDetailComponent,
          ),
      },
      {
        path: 'admin/dashboard',
        loadComponent: () =>
          import('./features/admin/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'admin/rules',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('./features/admin/rules.component').then((m) => m.RulesComponent),
      },
      {
        path: 'admin/knowledge',
        loadComponent: () =>
          import('./features/admin/knowledge.component').then((m) => m.KnowledgeComponent),
      },
      {
        path: 'admin/audit',
        canActivate: [roleGuard('CHIEF_ACCOUNTANT', 'ADMIN')],
        loadComponent: () =>
          import('./features/admin/audit-logs.component').then((m) => m.AuditLogsComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
