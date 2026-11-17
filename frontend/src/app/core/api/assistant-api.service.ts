import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth.service';
import { ChatEvent, MessageResponse, SessionResponse } from '../models/assistant.model';
import { PageResponse } from '../models/page.model';
import { readSse } from '../sse';

@Injectable({ providedIn: 'root' })
export class AssistantApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = '/api/v1/assistant';

  sessions(): Observable<PageResponse<SessionResponse>> {
    return this.http.get<PageResponse<SessionResponse>>(`${this.base}/sessions`);
  }

  createSession(): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${this.base}/sessions`, { channel: 'CHAT' });
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/sessions/${id}`);
  }

  messages(id: string): Observable<PageResponse<MessageResponse>> {
    return this.http.get<PageResponse<MessageResponse>>(`${this.base}/sessions/${id}/messages`, {
      params: new HttpParams().set('size', 200),
    });
  }

  /** Gửi câu hỏi và nhận sự kiện qua SSE. Có thể huỷ bằng `signal`. */
  async send(
    sessionId: string,
    content: string,
    context: Record<string, unknown> | undefined,
    onEvent: (e: ChatEvent) => void,
    signal?: AbortSignal,
  ): Promise<void> {
    const attempt = (token: string | null): Promise<Response> =>
      fetch(`${this.base}/sessions/${sessionId}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ content, context }),
        signal,
      });

    let res = await attempt(this.auth.token());
    if (res.status === 401 && this.auth.isLoggedIn()) {
      res = await attempt(await firstValueFrom(this.auth.refresh()));
    }
    if (!res.ok || !res.body) {
      const problem = (await res.json().catch(() => null)) as { detail?: string } | null;
      throw new Error(problem?.detail ?? `Lỗi ${res.status}`);
    }
    await readSse(res.body, onEvent);
  }
}
