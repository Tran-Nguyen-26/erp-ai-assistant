import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AssistantApiService } from '../../core/api/assistant-api.service';
import { errorMessage } from '../../core/http-error';
import {
  ChatEvent,
  CitationRef,
  MessageResponse,
  SessionResponse,
} from '../../core/models/assistant.model';

interface ChatItem {
  kind: 'user' | 'assistant' | 'tool';
  text: string;
  tool?: string;
  status?: string;
  citations?: CitationRef[];
  pending?: boolean;
}

@Component({
  selector: 'app-assistant-chat',
  standalone: true,
  imports: [FormsModule, MatButtonModule],
  template: `
    <div class="layout">
      <aside class="side">
        <button mat-flat-button (click)="newSession()">+ Cuộc trò chuyện mới</button>
        @for (s of sessions(); track s.id) {
          <div
            class="sess"
            [class.active]="s.id === current()?.id"
            (click)="select(s)"
            (keyup.enter)="select(s)"
            tabindex="0"
            role="button"
          >
            <span class="grow">{{ s.title ?? 'Cuộc trò chuyện mới' }}</span>
            <button mat-button (click)="remove(s, $event)" aria-label="Xóa">✕</button>
          </div>
        }
      </aside>

      <section class="chat">
        <div #scroller class="messages">
          @if (!current()) {
            <p class="muted">
              Chọn hoặc tạo một cuộc trò chuyện. Ví dụ: “Tháng 10 có bao nhiêu hóa đơn mua vào?”,
              “Tài khoản 331 dùng để làm gì?”.
            </p>
          }
          @for (m of items(); track $index) {
            @switch (m.kind) {
              @case ('user') {
                <div class="bubble user">{{ m.text }}</div>
              }
              @case ('tool') {
                <div class="tool">
                  🔧 <strong>{{ m.tool }}</strong> — {{ m.text }}
                  @if (m.status && m.status !== 'OK') {
                    <span class="chip WARNING">{{ m.status }}</span>
                  }
                </div>
              }
              @case ('assistant') {
                <div class="bubble bot">
                  <span style="white-space: pre-wrap">{{ m.text }}</span>
                  @if (m.pending) {
                    <span class="cursor">▍</span>
                  }
                  @if (m.citations?.length) {
                    <div class="cites">
                      @for (c of m.citations; track c.index) {
                        <div>[{{ c.index }}] {{ c.docNumber }} — {{ c.headingPath }}</div>
                      }
                    </div>
                  }
                </div>
              }
            }
          }
        </div>
        <form class="input" (ngSubmit)="send()">
          <input
            name="q"
            [(ngModel)]="question"
            placeholder="Nhập câu hỏi nghiệp vụ…"
            maxlength="4000"
            [disabled]="busy() || !current()"
            autocomplete="off"
          />
          @if (busy()) {
            <button mat-stroked-button type="button" (click)="stop()">Dừng</button>
          } @else {
            <button mat-flat-button type="submit" [disabled]="!question.trim() || !current()">
              Gửi
            </button>
          }
        </form>
      </section>
    </div>
  `,
  styles: [
    `
      .layout {
        display: grid;
        grid-template-columns: 260px 1fr;
        height: calc(100vh - 64px);
      }
      .side {
        border-right: 1px solid var(--mat-sys-outline-variant);
        padding: 12px;
        overflow: auto;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .sess {
        display: flex;
        align-items: center;
        padding: 6px 8px;
        border-radius: 8px;
        cursor: pointer;
      }
      .sess.active,
      .sess:hover {
        background: var(--mat-sys-surface-container);
      }
      .chat {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .messages {
        flex: 1;
        overflow: auto;
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .bubble {
        max-width: 780px;
        padding: 10px 14px;
        border-radius: 14px;
      }
      .user {
        align-self: flex-end;
        background: var(--mat-sys-primary-container);
        color: var(--mat-sys-on-primary-container);
      }
      .bot {
        align-self: flex-start;
        background: var(--mat-sys-surface-container);
      }
      .tool {
        align-self: flex-start;
        font-size: 13px;
        color: var(--mat-sys-on-surface-variant);
      }
      .cites {
        margin-top: 8px;
        font-size: 12px;
        color: var(--mat-sys-on-surface-variant);
        border-top: 1px solid var(--mat-sys-outline-variant);
        padding-top: 6px;
      }
      .cursor {
        animation: blink 1s steps(2) infinite;
      }
      @keyframes blink {
        50% {
          opacity: 0;
        }
      }
      .input {
        display: flex;
        gap: 8px;
        padding: 12px;
        border-top: 1px solid var(--mat-sys-outline-variant);
      }
      .input input {
        flex: 1;
        padding: 10px 12px;
        border-radius: 8px;
        border: 1px solid var(--mat-sys-outline);
        background: var(--mat-sys-surface);
        color: inherit;
      }
      @media (max-width: 720px) {
        .layout {
          grid-template-columns: 1fr;
        }
        .side {
          display: none;
        }
      }
    `,
  ],
})
export class AssistantChatComponent implements OnInit, OnDestroy {
  private readonly api = inject(AssistantApiService);
  private readonly snack = inject(MatSnackBar);

  @ViewChild('scroller') private scroller?: ElementRef<HTMLElement>;

  protected readonly sessions = signal<SessionResponse[]>([]);
  protected readonly current = signal<SessionResponse | null>(null);
  protected readonly items = signal<ChatItem[]>([]);
  protected readonly busy = signal(false);
  protected question = '';
  private abort?: AbortController;

  ngOnInit(): void {
    this.api.sessions().subscribe({
      next: (p) => {
        this.sessions.set(p.items);
        if (p.items.length) {
          this.select(p.items[0]);
        }
      },
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  ngOnDestroy(): void {
    this.abort?.abort();
  }

  protected newSession(): void {
    this.api.createSession().subscribe({
      next: (s) => {
        this.sessions.update((list) => [s, ...list]);
        this.select(s);
      },
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  protected select(s: SessionResponse): void {
    this.abort?.abort();
    this.busy.set(false);
    this.current.set(s);
    this.api.messages(s.id).subscribe({
      next: (p) => {
        this.items.set(p.items.map((m) => this.toItem(m)));
        this.scrollDown();
      },
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  protected remove(s: SessionResponse, event: Event): void {
    event.stopPropagation();
    this.api.deleteSession(s.id).subscribe({
      next: () => {
        this.sessions.update((list) => list.filter((x) => x.id !== s.id));
        if (this.current()?.id === s.id) {
          this.current.set(null);
          this.items.set([]);
        }
      },
      error: (e) => this.toast(errorMessage(e)),
    });
  }

  protected stop(): void {
    this.abort?.abort();
  }

  protected async send(): Promise<void> {
    const session = this.current();
    const q = this.question.trim();
    if (!session || !q || this.busy()) {
      return;
    }
    this.question = '';
    this.busy.set(true);
    this.items.update((list) => [
      ...list,
      { kind: 'user', text: q },
      { kind: 'assistant', text: '', pending: true },
    ]);
    this.abort = new AbortController();
    try {
      await this.api.send(session.id, q, undefined, (e) => this.onEvent(e), this.abort.signal);
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) {
        this.toast(e instanceof Error ? e.message : 'Không gửi được câu hỏi');
      }
    } finally {
      this.finishAssistant();
      this.busy.set(false);
      this.refreshSessionTitle(session.id);
    }
  }

  private onEvent(e: ChatEvent): void {
    switch (e.event) {
      case 'tool_call':
        this.insertBeforeAssistant({ kind: 'tool', tool: e.data.tool, text: 'đang chạy…' });
        break;
      case 'tool_result':
        this.updateLastTool(e.data.tool, e.data.summary, e.data.status);
        break;
      case 'token':
        this.updateAssistant((a) => ({ ...a, text: a.text + e.data.text }));
        break;
      case 'citations':
        this.updateAssistant((a) => ({ ...a, citations: e.data.citations }));
        break;
      case 'error':
        this.updateAssistant((a) => ({ ...a, text: a.text || `⚠ ${e.data.message}` }));
        break;
      default:
        break;
    }
    this.scrollDown();
  }

  private insertBeforeAssistant(item: ChatItem): void {
    this.items.update((list) => [...list.slice(0, -1), item, list[list.length - 1]]);
  }

  private updateLastTool(tool: string, summary: string, status: string): void {
    this.items.update((list) => {
      const copy = [...list];
      for (let i = copy.length - 1; i >= 0; i--) {
        if (copy[i].kind === 'tool' && copy[i].tool === tool && copy[i].text === 'đang chạy…') {
          copy[i] = { ...copy[i], text: summary, status };
          break;
        }
      }
      return copy;
    });
  }

  private updateAssistant(fn: (a: ChatItem) => ChatItem): void {
    this.items.update((list) => [...list.slice(0, -1), fn(list[list.length - 1])]);
  }

  private finishAssistant(): void {
    this.items.update((list) => {
      const last = list[list.length - 1];
      return last?.kind === 'assistant'
        ? [...list.slice(0, -1), { ...last, pending: false }]
        : list;
    });
  }

  private refreshSessionTitle(id: string): void {
    this.api.sessions().subscribe((p) => {
      this.sessions.set(p.items);
      const updated = p.items.find((s) => s.id === id);
      if (updated) {
        this.current.set(updated);
      }
    });
  }

  private toItem(m: MessageResponse): ChatItem {
    if (m.role === 'USER') {
      return { kind: 'user', text: m.content ?? '' };
    }
    if (m.role === 'TOOL') {
      return {
        kind: 'tool',
        tool: m.toolName ?? '',
        text: m.toolResult?.summary ?? '',
        status: m.toolResult?.status,
      };
    }
    return { kind: 'assistant', text: m.content ?? '', citations: m.citations };
  }

  private scrollDown(): void {
    setTimeout(() => {
      const el = this.scroller?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }

  private toast(m: string): void {
    this.snack.open(m, 'Đóng', { duration: 5000 });
  }
}
