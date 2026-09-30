package com.nuwainfo.javaape;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Request-scoped Java references made available to a Host Services peer. */
final class HostReferenceArena {
  private final long callId;
  private final IdentityHashMap<Object, Long> identifiers = new IdentityHashMap<>();
  private final Map<Long, Object> values = new HashMap<>();
  private long nextIdentifier = 1;

  HostReferenceArena(long callId) {
    this.callId = callId;
  }

  long callId() {
    return callId;
  }

  long add(Object value) {
    Long identifier = identifiers.get(value);

    if (identifier != null) {
      return identifier;
    }

    long result = nextIdentifier++;
    identifiers.put(value, result);
    values.put(result, value);
    return result;
  }

  Object require(long identifier) {
    Object value = values.get(identifier);

    if (value == null) {
      throw new HostServices.HostProtocolException("unknown Java reference " + identifier);
    }

    return value;
  }

  void release(long identifier) {
    Object value = values.remove(identifier);

    if (value != null) {
      identifiers.remove(value);
    }
  }
}
