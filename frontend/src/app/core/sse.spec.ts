import { ChatEvent } from './models/assistant.model';
import { parseBlock, readSse } from './sse';

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const enc = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(enc.encode(c)));
      controller.close();
    },
  });
}

describe('SSE parser', () => {
  it('parses an event block and ignores ping', () => {
    expect(parseBlock('event:token\ndata:{"text":"Xin chào"}')).toEqual({
      event: 'token',
      data: { text: 'Xin chào' },
    });
    expect(parseBlock('event:ping\ndata:{}')).toBeNull();
    expect(parseBlock('data:{"a":1}')).toBeNull();
    expect(parseBlock('event:token\ndata:không phải json')).toBeNull();
  });

  it('reassembles events split across network chunks, including multi-byte characters', async () => {
    const payload =
      'event:token\ndata:{"text":"hóa đơn"}\n\nevent:done\ndata:{"messageId":"m","latencyMs":5,"steps":1}\n\n';
    const bytes = new TextEncoder().encode(payload);
    // cắt giữa một ký tự UTF-8 nhiều byte để kiểm tra decoder dạng stream
    const cut = payload.indexOf('ó') + 1;
    const first = bytes.slice(0, new TextEncoder().encode(payload.slice(0, cut)).length - 1);
    const second = bytes.slice(first.length);
    const events: ChatEvent[] = [];
    const stream = new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(first);
        c.enqueue(second);
        c.close();
      },
    });

    await readSse(stream, (e) => events.push(e));

    expect(events.map((e) => e.event)).toEqual(['token', 'done']);
    expect((events[0] as Extract<ChatEvent, { event: 'token' }>).data.text).toBe('hóa đơn');
  });

  it('delivers events in order and stops at stream end', async () => {
    const got: string[] = [];
    await readSse(
      streamOf([
        'event:message_start\ndata:{"messageId":"1","sessionId":"2"}\n\n',
        'event:error\ndata:{"code":"X","message":"m"}\n\n',
      ]),
      (e) => got.push(e.event),
    );
    expect(got).toEqual(['message_start', 'error']);
  });
});
