package com.nuwainfo.javaape;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Queues host VM requests for execution by the Java thread awaiting one native call. */
final class HostCallSession {
  private final HostReferenceArena arena;
  private final BlockingQueue<PendingRequest> pendingRequests = new LinkedBlockingQueue<>();

  HostCallSession(HostReferenceArena arena) {
    this.arena = arena;
  }

  long callId() {
    return arena.callId();
  }

  CompletableFuture<Map<String, Object>> submit(String method, Map<String, Object> params) {
    CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
    pendingRequests.add(new PendingRequest(method, params, result));
    return result;
  }

  void executeNext(long timeout, TimeUnit unit) throws InterruptedException {
    PendingRequest request = pendingRequests.poll(timeout, unit);

    if (request == null) {
      return;
    }

    try {
      request.result.complete(JavaEnvironment.dispatch(request.method, request.params, arena));
    } catch (Throwable error) {
      request.result.completeExceptionally(error);
    }
  }

  private static final class PendingRequest {
    private final String method;
    private final Map<String, Object> params;
    private final CompletableFuture<Map<String, Object>> result;

    private PendingRequest(
        String method, Map<String, Object> params, CompletableFuture<Map<String, Object>> result) {
      this.method = method;
      this.params = params;
      this.result = result;
    }
  }
}
