package com.sz.aiagent.service;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RunRegistryTest {
  @Test
  void cancelIsBoundToSessionAndIdempotent() {
    var registry = new RunRegistry();
    var calls = new AtomicInteger();
    registry.register("alice", "run-1").onCancel(calls::incrementAndGet);
    registry.cancel("bob", "run-1");
    assertThat(calls).hasValue(0);
    registry.cancel("alice", "run-1");
    registry.cancel("alice", "run-1");
    assertThat(calls).hasValue(1);
    registry.register("alice", "run-1").complete();
  }

  @Test
  void cancelBeforeTaskRegistrationStillStopsTask() {
    var registry = new RunRegistry();
    var calls = new AtomicInteger();
    var run = registry.register("alice", "run-1");
    registry.cancel("alice", "run-1");
    run.onCancel(calls::incrementAndGet);
    assertThat(calls).hasValue(1);
  }
}
