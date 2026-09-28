package com.nuwainfo.javaape;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class HostRequest {
  private static final int VERSION = 1;

  private final String header;
  private final byte[] binary;

  private HostRequest(String header, byte[] binary) {
    this.header = header;
    this.binary = binary;
  }

  static HostRequest create(String service, Object[] arguments) {
    StringBuilder json = new StringBuilder();
    ByteArrayOutputStream binary = new ByteArrayOutputStream();

    json.append("{\"version\":").append(VERSION).append(",\"service\":");
    HostValueCodec.appendString(json, service);
    json.append(",\"arguments\":[");

    for (int index = 0; index < arguments.length; ++index) {
      if (index != 0) {
        json.append(',');
      }

      HostValueCodec.appendRequestValue(json, binary, arguments[index]);
    }

    json.append("]}");
    return new HostRequest(json.toString(), binary.toByteArray());
  }

  byte[] wire() throws IOException {
    byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream body = new ByteArrayOutputStream(4 + headerBytes.length + binary.length);

    try (DataOutputStream output = new DataOutputStream(body)) {
      output.writeInt(headerBytes.length);
      output.write(headerBytes);
      output.write(binary);
    }

    return body.toByteArray();
  }
}
