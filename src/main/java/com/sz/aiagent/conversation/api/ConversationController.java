package com.sz.aiagent.conversation.api;

import com.sz.aiagent.conversation.application.ConversationService;
import com.sz.aiagent.conversation.domain.ApiFailure;
import com.sz.aiagent.conversation.infrastructure.ConversationStore;
import jakarta.servlet.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/v1")
public class ConversationController {
  private final ConversationStore store;
  private final ConversationService service;
  private final BrowserIdentity identity;

  public ConversationController(
      ConversationStore store, ConversationService service, BrowserIdentity identity) {
    this.store = store;
    this.service = service;
    this.identity = identity;
  }

  public record ConversationInput(String title, String mode, String constraints) {}

  public record MessageInput(String message, String requestId) {}

  private String owner(HttpServletRequest req, HttpServletResponse res) {
    res.setHeader("Cache-Control", "no-store");
    return identity.resolve(req, res);
  }

  private static String value(String s, int max, boolean optional) {
    if (s == null) s = "";
    s = s.trim();
    if ((!optional && s.isEmpty()) || s.length() > max)
      throw new ApiFailure(400, "INVALID_INPUT", "输入为空或超过长度限制。");
    return s;
  }

  private static void mutation(String header) {
    if (!"web".equals(header)) throw new ApiFailure(403, "CLIENT_HEADER_REQUIRED", "缺少请求校验标识。");
  }

  @GetMapping("/conversations")
  public Object list(HttpServletRequest req, HttpServletResponse res) {
    return store.list(owner(req, res));
  }

  @PostMapping("/conversations")
  public Object create(
      @RequestBody ConversationInput input,
      @RequestHeader(value = "X-Travel-Client", required = false) String header,
      HttpServletRequest req,
      HttpServletResponse res) {
    mutation(header);
    String mode = input.mode() == null ? "CHAT" : input.mode();
    if (!Set.of("CHAT", "AGENT").contains(mode))
      throw new ApiFailure(400, "INVALID_MODE", "无效会话模式。");
    return store.create(
        owner(req, res),
        value(input.title(), 120, false),
        mode,
        value(input.constraints(), 2000, true));
  }

  @GetMapping("/conversations/{cid}")
  public Object detail(@PathVariable String cid, HttpServletRequest req, HttpServletResponse res) {
    String owner = owner(req, res);
    return Map.of(
        "conversation",
        store.conversation(owner, cid),
        "messages",
        store.messages(owner, cid),
        "runs",
        store.runs(owner, cid));
  }

  @PatchMapping("/conversations/{cid}")
  public Object update(
      @PathVariable String cid,
      @RequestBody ConversationInput input,
      @RequestHeader(value = "X-Travel-Client", required = false) String header,
      HttpServletRequest req,
      HttpServletResponse res) {
    mutation(header);
    String owner = owner(req, res);
    store.update(
        owner, cid, value(input.title(), 120, false), value(input.constraints(), 2000, true));
    return store.conversation(owner, cid);
  }

  @PostMapping("/conversations/{cid}/messages")
  public Object send(
      @PathVariable String cid,
      @RequestBody MessageInput input,
      @RequestHeader(value = "X-Travel-Client", required = false) String header,
      HttpServletRequest req,
      HttpServletResponse res) {
    mutation(header);
    String key = value(input.requestId(), 80, false);
    if (!key.matches("[A-Za-z0-9-]+")) throw new ApiFailure(400, "INVALID_REQUEST_ID", "无效请求标识。");
    return service.send(owner(req, res), cid, key, value(input.message(), 8000, false));
  }

  @GetMapping("/runs/{id}")
  public Object run(@PathVariable String id, HttpServletRequest req, HttpServletResponse res) {
    var run = store.ownedRun(owner(req, res), id);
    return Map.of(
        "run",
        run,
        "usage",
        store.usage(id),
        "tools",
        store.tools(id),
        "events",
        store.events(id, 0));
  }

  @PostMapping("/runs/{id}/cancel")
  public Object cancel(
      @PathVariable String id,
      @RequestHeader(value = "X-Travel-Client", required = false) String header,
      HttpServletRequest req,
      HttpServletResponse res) {
    mutation(header);
    service.cancel(owner(req, res), id);
    return Map.of("status", "ok");
  }

  @GetMapping(value = "/runs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Map<String, Object>>> events(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") long after,
      @RequestHeader(value = "Last-Event-ID", required = false) String last,
      HttpServletRequest req,
      HttpServletResponse res) {
    var run = store.ownedRun(owner(req, res), id);
    long cursor = after;
    try {
      if (last != null) cursor = Math.max(cursor, Long.parseLong(last));
    } catch (NumberFormatException e) {
      throw new ApiFailure(400, "INVALID_CURSOR", "无效事件序号。");
    }
    if (cursor < 0 || cursor > run.lastEvent())
      throw new ApiFailure(400, "INVALID_CURSOR", "无效事件序号。");
    res.setHeader("X-Accel-Buffering", "no");
    AtomicLong position = new AtomicLong(cursor);
    return Flux.interval(Duration.ZERO, Duration.ofMillis(500))
        .onBackpressureDrop()
        .concatMap(
            tick ->
                reactor.core.publisher.Mono.fromCallable(
                        () -> {
                          List<ServerSentEvent<Map<String, Object>>> result = new ArrayList<>();
                          for (var event : store.events(id, position.get())) {
                            position.set(event.seq());
                            result.add(
                                ServerSentEvent.builder(event.data())
                                    .id(String.valueOf(event.seq()))
                                    .event(event.type())
                                    .build());
                          }
                          if (result.isEmpty()) {
                            var current = store.run(id);
                            if (!Set.of("QUEUED", "RUNNING").contains(current.status()))
                              result.add(
                                  ServerSentEvent.builder(
                                          Map.<String, Object>of(
                                              "state",
                                              current.status(),
                                              "errorCode",
                                              current.errorCode() == null
                                                  ? ""
                                                  : current.errorCode()))
                                      .event("done")
                                      .build());
                            else if (tick % 30 == 0)
                              result.add(
                                  ServerSentEvent.<Map<String, Object>>builder()
                                      .comment("keepalive")
                                      .build());
                          }
                          return result;
                        })
                    .subscribeOn(Schedulers.boundedElastic()))
        .flatMapIterable(items -> items)
        .takeUntil(event -> "done".equals(event.event()))
        .timeout(Duration.ofMinutes(5));
  }
}
