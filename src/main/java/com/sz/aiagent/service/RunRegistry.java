package com.sz.aiagent.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RunRegistry {
  private final ConcurrentHashMap<String, Registration> runs = new ConcurrentHashMap<>();

  private String key(String session, String id) {
    if (id == null || !id.matches("[A-Za-z0-9-]{1,80}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效请求 ID");
    return session + ":" + id;
  }

  public Registration register(String session, String id) {
    String key = key(session, id);
    Registration registration = new Registration(key, runs);
    if (runs.putIfAbsent(key, registration) != null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "请求已存在");
    return registration;
  }

  public void cancel(String session, String id) {
    var registration = runs.get(key(session, id));
    if (registration != null) registration.cancel(); // Idempotent; other sessions cannot cancel it.
  }

  public static class Registration {
    private Runnable stop;
    private final String key;
    private final ConcurrentHashMap<String, Registration> runs;
    private boolean cancelled;

    Registration(String key, ConcurrentHashMap<String, Registration> runs) {
      this.key = key;
      this.runs = runs;
    }

    public synchronized void onCancel(Runnable stop) {
      this.stop = stop;
      if (cancelled) stop.run();
    }

    public synchronized void cancel() {
      if (cancelled) return;
      cancelled = true;
      try {
        if (stop != null) stop.run();
      } finally {
        runs.remove(key, this);
      }
    }

    public void complete() {
      runs.remove(key, this);
    }
  }
}
