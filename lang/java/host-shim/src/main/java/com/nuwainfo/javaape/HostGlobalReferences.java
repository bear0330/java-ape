package com.nuwainfo.javaape;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Process-scoped strong Java references explicitly owned by a Host Services provider. */
final class HostGlobalReferences {
  private static final AtomicLong NEXT_IDENTIFIER = new AtomicLong(1);
  private static final ConcurrentHashMap<Long, Object> VALUES = new ConcurrentHashMap<>();

  private HostGlobalReferences() {
  }

  static HostGlobalReference add(Object value) {
    if (value == null) {
      throw new HostServices.HostProtocolException("cannot create a global reference to null");
    }

    long identifier = NEXT_IDENTIFIER.getAndIncrement();
    VALUES.put(identifier, value);
    return new HostGlobalReference(identifier);
  }

  static Object require(long identifier) {
    Object value = VALUES.get(identifier);

    if (value == null) {
      throw new HostServices.HostProtocolException("unknown global Java reference " + identifier);
    }

    return value;
  }

  static void release(long identifier) {
    if (VALUES.remove(identifier) == null) {
      throw new HostServices.HostProtocolException("unknown global Java reference " + identifier);
    }
  }
}
