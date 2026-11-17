import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './http-error';

describe('errorMessage', () => {
  it('uses Problem Details detail and code', () => {
    const err = new HttpErrorResponse({
      status: 422,
      error: { detail: 'Kỳ không hợp lệ', code: 'ERP_INVALID_PERIOD' },
    });
    expect(errorMessage(err)).toBe('Kỳ không hợp lệ (ERP_INVALID_PERIOD)');
  });

  it('reports network failure and falls back to the status', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe('Không kết nối được máy chủ');
    expect(errorMessage(new HttpErrorResponse({ status: 500 }))).toBe('Lỗi 500');
    expect(errorMessage('x')).toBe('Đã xảy ra lỗi');
  });
});
