import test from "node:test";
import assert from "node:assert/strict";
import { observeRun, request } from "./travelApi.js";
class Source {
  static last;
  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.readyState = 0;
    Source.last = this;
  }
  addEventListener(type, callback) {
    this.listeners[type] = callback;
  }
  close() {
    this.closed = true;
  }
  emit(type, data, seq) {
    this.listeners[type]({
      data: JSON.stringify(data),
      lastEventId: String(seq),
    });
  }
}
test("replayed events are deduplicated and late callbacks after close cannot write", () => {
  const seen = [];
  const connection = observeRun(
    "run",
    0,
    { event: (t, d) => seen.push(d.content), error: () => assert.fail() },
    Source,
  );
  Source.last.emit("delta", { content: "a" }, 1);
  Source.last.emit("delta", { content: "a" }, 1);
  connection.close();
  Source.last.emit("delta", { content: "late" }, 2);
  assert.deepEqual(seen, ["a"]);
});
test("disconnect reconnects observation without creating another request", () => {
  let retry = 0;
  observeRun(
    "run",
    3,
    {
      event: () => {},
      error: () => assert.fail(),
      reconnecting: () => retry++,
    },
    Source,
  );
  Source.last.onerror();
  assert.equal(retry, 1);
  assert.equal(Source.last.closed, undefined);
  assert.ok(Source.last.url.endsWith("/runs/run/events?after=3"));
});
test("terminal event closes the subscription", () => {
  const seen = [];
  observeRun(
    "run",
    0,
    { event: (t) => seen.push(t), error: () => assert.fail() },
    Source,
  );
  Source.last.emit("done", { state: "CANCELLED" }, 1);
  Source.last.emit("delta", { content: "late" }, 2);
  assert.deepEqual(seen, ["done"]);
  assert.equal(Source.last.closed, true);
});
test("POST keeps message in JSON and sends ownership credentials plus CSRF header", async () => {
  await request(
    "/conversations/c/messages",
    { method: "POST", body: JSON.stringify({ message: "深圳" }) },
    async (url, options) => {
      assert.equal(url.includes("深圳"), false);
      assert.equal(options.credentials, "include");
      assert.equal(options.headers["X-Travel-Client"], "web");
      assert.equal(JSON.parse(options.body).message, "深圳");
      return { ok: true, json: async () => ({ id: "run" }) };
    },
  );
});

test("a terminal replay at the resume cursor still closes the stream", () => {
  const seen = [];
  observeRun(
    "run",
    4,
    { event: (t) => seen.push(t), error: () => assert.fail() },
    Source,
  );
  Source.last.emit("done", { state: "FINISHED" }, 4);
  assert.deepEqual(seen, ["done"]);
  assert.equal(Source.last.closed, true);
});
