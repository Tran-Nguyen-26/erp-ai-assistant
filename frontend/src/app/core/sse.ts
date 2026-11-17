import { ChatEvent } from './models/assistant.model';

/**
 * Phân tích luồng SSE từ một fetch POST. `EventSource` của trình duyệt chỉ hỗ trợ GET nên phải tự đọc
 * ReadableStream. Trả về khi luồng kết thúc.
 */
export async function readSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (e: ChatEvent) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const parsed = parseBlock(block);
      if (parsed) {
        onEvent(parsed);
      }
    }
  }
}

export function parseBlock(block: string): ChatEvent | null {
  let name = '';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trim());
    }
  }
  if (!name || name === 'ping' || data.length === 0) {
    return null;
  }
  try {
    return { event: name, data: JSON.parse(data.join('\n')) } as ChatEvent;
  } catch {
    return null;
  }
}
