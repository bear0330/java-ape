package com.nuwainfo.javaape;

import java.util.Map;

/** Decodes a successful JSON-RPC result from a Host Services provider. */
final class HostResponse {
  private HostResponse() {
  }

  static HostInvocation invocation(Object encoded, HostReferenceArena arena) {
    Map<String, Object> result = HostValueCodec.requireMap(
        encoded, "host JSON-RPC result is not an object");
    Object descriptor = result.get("return");
    Object returnValue = null;

    if (descriptor != null) {
      Map<String, Object> value = HostValueCodec.requireMap(
          descriptor, "host return descriptor is not an object");
      returnValue = HostValueCodec.decodeResponseValue(value, new byte[0]);
    }

    return new HostInvocation(
        returnValue,
        HostMutation.decode(result.get("mutations"), new byte[0]),
        exception(result.get("exception"), arena));
  }

  private static Throwable exception(Object encoded, HostReferenceArena arena) {
    if (encoded == null) {
      return null;
    }

    Map<String, Object> descriptor = HostValueCodec.requireMap(
        encoded, "host exception descriptor is not an object");
    long reference = HostValueCodec.requireInteger(
        descriptor.get("ref"), "host exception reference");
    Object value = arena.require(reference);

    if (!(value instanceof Throwable)) {
      throw new HostServices.HostProtocolException("host exception reference is not a Throwable");
    }

    return (Throwable) value;
  }

}
