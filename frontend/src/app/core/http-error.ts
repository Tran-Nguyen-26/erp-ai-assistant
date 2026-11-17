import { HttpErrorResponse } from '@angular/common/http';

/** Lấy thông điệp dễ đọc từ Problem Details (RFC 7807) của backend. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { detail?: string; code?: string } | null;
    if (body?.detail) {
      return body.code ? `${body.detail} (${body.code})` : body.detail;
    }
    if (err.status === 0) {
      return 'Không kết nối được máy chủ';
    }
    return `Lỗi ${err.status}`;
  }
  return 'Đã xảy ra lỗi';
}
