import test from 'node:test';
import assert from 'node:assert/strict';
import { openChatStream } from './chatStream.js';
class FakeSource {
  static last;
  listeners = {}; closed = false;
  constructor() { FakeSource.last = this; }
  addEventListener(name, fn) { this.listeners[name] = fn; }
  close() { this.closed = true; }
  emit(name, data) { this.listeners[name]?.({ data: JSON.stringify(data) }); }
}
function setup() {
  const calls = [];
  const handlers = Object.fromEntries(['Delta','Step','Final','Notice','Done','Error'].map(type =>
    ['on' + type, value => calls.push([type, value])]));
  const stream = openChatStream('/test', handlers, FakeSource);
  return { stream, source: FakeSource.last, calls };
}
test('completion closes without reporting a transport error or replaying a request', () => {
  const { source, calls } = setup();
  source.emit('delta', { content: '深圳' });
  source.emit('done', {}); source.onerror();
  assert.equal(source.closed, true);
  assert.deepEqual(calls, [['Delta', '深圳'], ['Done', undefined]]);
});
test('cancelled request cannot write into a later reply', () => {
  const first = setup(); first.stream.close();
  const second = setup();
  first.source.emit('delta', { content: '迟到的消息' });
  second.source.emit('delta', { content: '新回复' });
  assert.deepEqual(first.calls, []);
  assert.deepEqual(second.calls, [['Delta', '新回复']]);
});
test('final answer is separate from tool progress', () => {
  const { source, calls } = setup();
  source.emit('step', { content: '检索完成' }); source.emit('final', { content: '行程' });
  assert.deepEqual(calls, [['Step', '检索完成'], ['Final', '行程']]);
});
test('transport failure closes once and preserves already emitted text', () => {
  const { source, calls } = setup(); source.emit('delta', { content: '部分' });
  source.onerror(); source.onerror();
  assert.equal(source.closed, true); assert.equal(calls.length, 2); assert.equal(calls[1][0], 'Error');
});
test('malformed event and application failure are terminal', () => {
  const first = setup(); first.source.listeners.delta({data: 'invalid'});
  assert.equal(first.source.closed, true); assert.equal(first.calls[0][0], 'Error');
  const next = setup(); next.source.emit('failure', {content: '模型不可用'}); next.source.emit('delta', {content: 'ignored'});
  assert.deepEqual(next.calls, [['Error', '模型不可用']]);
});
