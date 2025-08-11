/** Owns one connection. Closing it also prevents already queued events from leaking. */
export function openChatStream(url, handlers, EventSourceImpl = globalThis.EventSource) {
  const source = new EventSourceImpl(url, { withCredentials: true });
  let closed = false;
  const close = () => { if (!closed) { closed = true; source.close(); } };
  const listen = (event, callback) => source.addEventListener(event, e => {
    if (closed) return;
    try { callback(JSON.parse(e.data)); }
    catch { close(); handlers.onError('服务返回了无效的数据，请重试。'); }
  });
  listen('delta', data => handlers.onDelta(data.content || ''));
  listen('step', data => handlers.onStep(data.content || ''));
  listen('final', data => handlers.onFinal(data.content || ''));
  listen('limit', data => handlers.onNotice(data.content));
  listen('done', () => { close(); handlers.onDone(); });
  listen('failure', data => { close(); handlers.onError(data.content || '生成失败，请重试。'); });
  source.onerror = () => {
    if (closed) return;
    close(); // Never let EventSource automatically replay a paid model request.
    handlers.onError('连接中断，已保留收到的内容，请重试。');
  };
  return { close };
}
