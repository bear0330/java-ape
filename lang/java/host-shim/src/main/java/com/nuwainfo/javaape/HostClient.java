package com.nuwainfo.javaape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class HostClient {
  private static final String HOST_ENV = "JAVA_APE_HOST";
  private static final String TOKEN_ENV = "JAVA_APE_HOST_TOKEN";
  private static final String CONTENT_TYPE = "application/vnd.java-ape-host; version=1";
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 60_000;

  private HostClient() {
  }

  static Object invoke(String service, Object[] arguments) {
    String host = requiredEnvironment(HOST_ENV);
    String token = requiredEnvironment(TOKEN_ENV);

    try {
      HostRequest request = HostRequest.create(service, arguments);
      HttpURLConnection connection = openConnection(host, token, request.wire());

      return readResult(connection);
    } catch (IOException error) {
      throw new HostServices.HostUnavailableException("cannot call Java APE host", error);
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      throw new HostServices.HostUnavailableException(
          name + " must be set for Java APE Host Services");
    }

    return value;
  }

  private static HttpURLConnection openConnection(String host, String token, byte[] body)
      throws IOException {
    URL endpoint = new URL(invokeEndpoint(host));
    HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setFixedLengthStreamingMode(body.length);
    connection.setRequestProperty("Content-Type", CONTENT_TYPE);
    connection.setRequestProperty("Accept", CONTENT_TYPE);
    connection.setRequestProperty("Authorization", "Bearer " + token);

    try (java.io.OutputStream output = connection.getOutputStream()) {
      output.write(body);
    }

    return connection;
  }

  private static String invokeEndpoint(String host) {
    if (host.endsWith("/")) {
      return host + "v1/invoke";
    }

    return host + "/v1/invoke";
  }

  private static Object readResult(HttpURLConnection connection) throws IOException {
    int status = connection.getResponseCode();
    byte[] wire = readAll(responseStream(connection, status));
    HostResponse response = HostResponse.parse(wire);

    if (status >= 200 && status < 300) {
      return response.value();
    }

    response.throwIfError();
    throw new HostServices.HostProtocolException(
        "host returned HTTP " + status + ": " + new String(wire, StandardCharsets.UTF_8));
  }

  private static InputStream responseStream(HttpURLConnection connection, int status) throws IOException {
    if (status >= 200 && status < 300) {
      return connection.getInputStream();
    }

    return connection.getErrorStream();
  }

  private static byte[] readAll(InputStream input) throws IOException {
    if (input == null) {
      return new byte[0];
    }

    try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;

      while ((count = stream.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }

      return output.toByteArray();
    }
  }
}
