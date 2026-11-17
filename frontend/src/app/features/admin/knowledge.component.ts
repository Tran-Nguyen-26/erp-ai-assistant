import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/api/admin-api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error';
import { KnowledgeDocumentResponse, RetrievalResult } from '../../core/models/admin.model';

@Component({
  selector: 'app-knowledge',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="page">
      <h1>Kho tri thức</h1>

      @if (auth.hasRole('ADMIN')) {
        <div class="card">
          <h3>Tải văn bản lên (pdf, txt, md)</h3>
          <div class="toolbar-row">
            <input type="file" accept=".pdf,.txt,.md" (change)="pick($event)" />
            <mat-form-field appearance="outline"
              ><mat-label>Tiêu đề</mat-label><input matInput [(ngModel)]="title"
            /></mat-form-field>
            <mat-form-field appearance="outline"
              ><mat-label>Số hiệu</mat-label><input matInput [(ngModel)]="docNumber"
            /></mat-form-field>
            <mat-form-field appearance="outline"
              ><mat-label>Loại</mat-label>
              <mat-select [(ngModel)]="docType">
                @for (t of types; track t) {
                  <mat-option [value]="t">{{ t }}</mat-option>
                }
              </mat-select></mat-form-field
            >
            <mat-form-field appearance="outline"
              ><mat-label>Hiệu lực từ</mat-label><input matInput type="date" [(ngModel)]="from"
            /></mat-form-field>
            <mat-form-field appearance="outline"
              ><mat-label>Đến</mat-label><input matInput type="date" [(ngModel)]="to"
            /></mat-form-field>
            <button mat-flat-button (click)="upload()" [disabled]="!file || !title">Tải lên</button>
          </div>
        </div>
      }

      <table class="full">
        <thead>
          <tr>
            <th>Tài liệu</th>
            <th>Loại</th>
            <th>Hiệu lực</th>
            <th>Trạng thái</th>
            <th>Chunk</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (d of docs(); track d.id) {
            <tr>
              <td>
                <strong>{{ d.docNumber ?? '—' }}</strong><br />{{ d.title }}
              </td>
              <td>{{ d.docType }}</td>
              <td>{{ d.effectiveFrom ?? '…' }} → {{ d.effectiveTo ?? '…' }}</td>
              <td>
                {{ d.status }}
                @if (d.errorMessage) {
                  <br /><span class="muted">{{ d.errorMessage }}</span>
                }
              </td>
              <td>{{ d.chunkCount }}</td>
              <td>
                @if (auth.hasRole('ADMIN')) {
                  <button mat-button (click)="reindex(d)">Nạp lại</button>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="muted">Chưa có tài liệu.</td>
            </tr>
          }
        </tbody>
      </table>

      <div class="card" style="margin-top:16px">
        <h3>Thử truy xuất (hybrid + rerank)</h3>
        <div class="toolbar-row">
          <mat-form-field appearance="outline" class="grow"
            ><mat-label>Câu hỏi</mat-label
            ><input matInput [(ngModel)]="query" (keyup.enter)="search()"
          /></mat-form-field>
          <mat-form-field appearance="outline"
            ><mat-label>Tại ngày</mat-label><input matInput type="date" [(ngModel)]="asOf"
          /></mat-form-field>
          <button mat-flat-button (click)="search()" [disabled]="!query.trim()">Tìm</button>
        </div>
        @if (result(); as r) {
          <p class="muted">
            {{ r.sufficient ? 'Đủ căn cứ' : 'KHÔNG đủ căn cứ' }} · điểm rerank cao nhất
            {{ r.maxScore }}
          </p>
          @for (c of r.chunks; track c.chunkId) {
            <div class="card">
              <strong>{{ c.docNumber ?? c.docTitle }}</strong> — {{ c.headingPath }}
              <span class="muted">(rerank {{ c.rerankScore }})</span>
              <p>{{ c.content }}</p>
            </div>
          }
        }
      </div>
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
export class KnowledgeComponent implements OnInit, OnDestroy {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly types = ['LAW', 'DECREE', 'CIRCULAR', 'MANUAL', 'INTERNAL'];
  protected readonly docs = signal<KnowledgeDocumentResponse[]>([]);
  protected readonly result = signal<RetrievalResult | null>(null);
  protected file: File | null = null;
  protected title = '';
  protected docNumber = '';
  protected docType = 'LAW';
  protected from = '';
  protected to = '';
  protected query = '';
  protected asOf = '';
  private timer: ReturnType<typeof setInterval> | undefined;

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 4000);
  }
  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  private load(): void {
    this.api.documents().subscribe({ next: (p) => this.docs.set(p.items), error: () => undefined });
  }

  protected pick(e: Event): void {
    this.file = (e.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected upload(): void {
    if (!this.file) {
      return;
    }
    this.api
      .uploadDocument(this.file, {
        title: this.title,
        docNumber: this.docNumber,
        docType: this.docType,
        effectiveFrom: this.from,
        effectiveTo: this.to,
      })
      .subscribe({
        next: () => {
          this.toast('Đã nhận, đang nạp…');
          this.load();
        },
        error: (e) => this.toast(errorMessage(e)),
      });
  }

  protected reindex(d: KnowledgeDocumentResponse): void {
    this.api
      .reindex(d.id)
      .subscribe({ next: () => this.load(), error: (e) => this.toast(errorMessage(e)) });
  }

  protected search(): void {
    this.api
      .searchKnowledge(this.query.trim(), this.asOf)
      .subscribe({ next: (r) => this.result.set(r), error: (e) => this.toast(errorMessage(e)) });
  }

  private toast(m: string): void {
    this.snack.open(m, 'Đóng', { duration: 5000 });
  }
}
