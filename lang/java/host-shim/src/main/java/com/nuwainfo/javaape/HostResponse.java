package com.nuwainfo.javaape;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class HostResponse {
  private static final int MAX_HEADER_BYTES = 1024 * 1024;

  private final Map<String, Object> header;
  private final byte[] binary;

  private HostResponse(Map<String, Object> header, byte[] binary) {
    this.header = header;
    this.binary = binary;
  }

  static HostResponse parse(byte[] wire) {
    if (wire.length < 4) {
      throw new HostServices.HostProtocolException("host response has no header");
    }

    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(wire))) {
      int headerLength = input.readInt();
      validateHeaderLength(headerLength, wire.length);

      byte[] headerBytes = new byte[headerLength];
      input.readFully(headerBytes);

      Object parsed = HostJson.parse(new String(headerBytes, StandardCharsets.UTF_8));
      Map<String, Object> header = HostValueCodec.requireMap(
          parsed, "host response header is not an object");

      return new HostResponse(header, input.readAllBytes());
    } catch (IOException error) {
      throw new HostServices.HostProtocolException("cannot parse host response", error);
    }
  }

  Object value() {
    throwIfError();

    Object descriptor = header.get("return");
    if (descriptor == null) {
      return null;
    }

    Map<String, Object> result = HostValueCodec.requireMap(
        descriptor, "host return descriptor is not an object");
    return HostValueCodec.decodeResponseValue(result, binary);
  }

  void throwIfError() {
    if (Boolean.TRUE.equals(header.get("ok"))) {
      return;
    }

    Map<String, Object> error = errorDescriptor();
    String type = errorType(error);
    String message = errorMessage(error);

    if ("unsupported".equals(type)) {
      throw new UnsupportedOperationException(message);
    }

    if ("unauthorized".equals(type)) {
      throw new SecurityException(message);
    }

    throw new HostServices.HostProtocolException(type + ": " + message);
  }

  private static void validateHeaderLength(int length, int wireLength) {
    if (length < 2 || length > MAX_HEADER_BYTES || length > wireLength - 4) {
      throw new HostServices.HostProtocolException("invalid host response header length");
    }
  }

  private Map<String, Object> errorDescriptor() {
    Object value = header.get("error");
    if (!(value instanceof Map)) {
      return null;
    }

    return HostValueCodec.requireMap(value, "host error descriptor is not an object");
  }

  private static String errorType(Map<String, Object> error) {
    if (error == null) {
      return "host";
    }

    Object value = error.get("type");
    if (value instanceof String) {
      return (String) value;
    }

    return "host";
  }

  private static String errorMessage(Map<String, Object> error) {
    if (error == null) {
      return "host request failed";
    }

    Object value = error.get("message");
    if (value instanceof String) {
      return (String) value;
    }

    return "host request failed";
  }
}
