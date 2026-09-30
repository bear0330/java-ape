package com.nuwainfo.javaape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;

/** LSP4J-backed persistent JSON-RPC peer for generic APE Host Services. */
final class HostClient {
  private static final String HOST_ENV = "APE_HOST";
  private static final String TOKEN_ENV = "APE_HOST_TOKEN";
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int REQUEST_TIMEOUT_SECONDS = 60;
  private static final Object CONNECT_LOCK = new Object();
  private static final AtomicLong NEXT_CALL_ID = new AtomicLong(1);

  private static HostClient connection;

  private final Socket socket;
  private final HostPeer host;
  private final ConcurrentHashMap<Long, HostCallSession> sessions = new ConcurrentHashMap<>();

  private HostClient(Socket socket, HostPeer host) {
    this.socket = socket;
    this.host = host;
  }

  static HostInvocation invokeService(String service, Object[] arguments) {
    HostReferenceArena arena = new HostReferenceArena(NEXT_CALL_ID.getAndIncrement());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("callId", arena.callId());
    params.put("service", service);
    params.put("arguments", encodeArguments(arguments, arena));
    return invoke(arena, client -> client.host.invokeService(params));
  }

  static HostInvocation invokeNative(
      String owner, String name, String descriptor, Object target, Object[] arguments) {
    HostReferenceArena arena = new HostReferenceArena(NEXT_CALL_ID.getAndIncrement());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("callId", arena.callId());
    params.put("owner", owner);
    params.put("name", name);
    params.put("descriptor", descriptor);
    params.put("target", HostValueCodec.encodeRequestValue(target, arena));
    params.put("arguments", encodeArguments(arguments, arena));
    return invoke(arena, client -> client.host.invokeJavaNative(params));
  }

  private static HostInvocation invoke(
      HostReferenceArena arena, HostRequestCall request) {
    HostClient client = connection();
    HostCallSession session = new HostCallSession(arena);
    client.sessions.put(session.callId(), session);

    try {
      return HostResponse.invocation(await(request.call(client), session), arena);
    } finally {
      client.sessions.remove(session.callId());
    }
  }

  private static HostClient connection() {
    synchronized (CONNECT_LOCK) {
      if (connection != null && !connection.socket.isClosed()) {
        return connection;
      }

      String endpoint = requiredEnvironment(HOST_ENV);
      String token = requiredEnvironment(TOKEN_ENV);
      Endpoint address = Endpoint.parse(endpoint);

      try {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(address.host, address.port), CONNECT_TIMEOUT_MS);
        if (!socket.getInetAddress().isLoopbackAddress()) {
          socket.close();
          throw new IOException("APE_HOST must resolve to a loopback address");
        }

        JavaPeer local = new JavaPeerService();
        ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
          Thread thread = new Thread(runnable, "ape-host-services");
          thread.setDaemon(true);
          return thread;
        });
        Launcher<HostPeer> launcher = Launcher.createLauncher(
            local, HostPeer.class, socket.getInputStream(), socket.getOutputStream(), executor, value -> value);
        launcher.startListening();
        HostClient client = new HostClient(socket, launcher.getRemoteProxy());
        ((JavaPeerService) local).bind(client.sessions);
        client.initialize(token);
        connection = client;
        return client;
      } catch (IOException error) {
        throw new HostServices.HostUnavailableException("cannot connect to APE Host Services", error);
      }
    }
  }

  private void initialize(String token) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("protocolVersions", List.of("1.0"));
    Map<String, Object> runtime = new LinkedHashMap<>();
    runtime.put("name", "java-ape");
    runtime.put("version", System.getProperty("java.version", "unknown"));
    params.put("runtime", runtime);
    params.put("token", token);
    params.put("capabilities", List.of("java.native", "java.env"));
    Map<String, Object> result = await(host.initialize(params));

    if (!"1.0".equals(result.get("protocolVersion"))) {
      throw new HostServices.HostProtocolException("host does not support APE Host Services 1.0");
    }
  }

  private static Map<String, Object> await(CompletableFuture<Map<String, Object>> call) {
    try {
      return call.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new HostServices.HostUnavailableException("APE Host Services call was interrupted", error);
    } catch (TimeoutException error) {
      throw new HostServices.HostUnavailableException("APE Host Services call timed out", error);
    } catch (ExecutionException error) {
      throw responseFailure(error.getCause());
    }
  }

  private static Map<String, Object> await(
      CompletableFuture<Map<String, Object>> call, HostCallSession session) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);

    try {
      while (!call.isDone()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new TimeoutException();
        }
        session.executeNext(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
      }
      return call.get();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new HostServices.HostUnavailableException("APE Host Services call was interrupted", error);
    } catch (TimeoutException error) {
      throw new HostServices.HostUnavailableException("APE Host Services call timed out", error);
    } catch (ExecutionException error) {
      throw responseFailure(error.getCause());
    }
  }

  private static RuntimeException responseFailure(Throwable error) {
    if (error instanceof ResponseErrorException) {
      ResponseErrorException response = (ResponseErrorException) error;
      String type = errorType(response.getResponseError().getData());
      String message = response.getResponseError().getMessage();
      if ("unsupported".equals(type)) {
        return new UnsupportedOperationException(message);
      }
      if ("unauthorized".equals(type)) {
        return new SecurityException(message);
      }
      return new HostServices.HostProtocolException(type + ": " + message, error);
    }
    if (error instanceof RuntimeException) {
      return (RuntimeException) error;
    }
    return new HostServices.HostUnavailableException("APE Host Services call failed", error);
  }

  private static String errorType(Object data) {
    if (data instanceof Map) {
      Object type = ((Map<?, ?>) data).get("type");
      if (type instanceof String) {
        return (String) type;
      }
    }
    if (data instanceof JsonObject) {
      JsonElement type = ((JsonObject) data).get("type");
      if (type != null && type.isJsonPrimitive()) {
        return type.getAsString();
      }
    }
    return "host";
  }

  private static List<Object> encodeArguments(Object[] arguments, HostReferenceArena arena) {
    List<Object> values = new ArrayList<>(arguments.length);
    for (Object argument : arguments) {
      values.add(HostValueCodec.encodeRequestValue(argument, arena));
    }
    return values;
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      throw new HostServices.HostUnavailableException(name + " must be set for APE Host Services");
    }
    return value;
  }

  private interface HostRequestCall {
    CompletableFuture<Map<String, Object>> call(HostClient client);
  }

  private static final class JavaPeerService implements JavaPeer {
    private ConcurrentHashMap<Long, HostCallSession> sessions;

    private void bind(ConcurrentHashMap<Long, HostCallSession> values) {
      sessions = values;
    }

    @Override
    public CompletableFuture<Map<String, Object>> execute(Map<String, Object> params) {
      return session(params).submit("java.env.execute", params);
    }

    @Override
    public CompletableFuture<Map<String, Object>> release(Map<String, Object> params) {
      return session(params).submit("java.ref.release", params);
    }

    private HostCallSession session(Map<String, Object> params) {
      long callId = HostValueCodec.requireInteger(params.get("callId"), "java.env call ID");
      HostCallSession result = sessions.get(callId);
      if (result == null) {
        throw new HostServices.HostProtocolException("no active Java native call " + callId);
      }
      return result;
    }
  }

  private static final class Endpoint {
    private final String host;
    private final int port;

    private Endpoint(String host, int port) {
      this.host = host;
      this.port = port;
    }

    private static Endpoint parse(String value) {
      String endpoint = value.startsWith("tcp://") ? value.substring(6) : value;
      int separator = endpoint.lastIndexOf(':');
      if (separator <= 0 || separator == endpoint.length() - 1) {
        throw new HostServices.HostUnavailableException("APE_HOST must be host:port");
      }
      String host = endpoint.substring(0, separator);
      try {
        int port = Integer.parseInt(endpoint.substring(separator + 1));
        if (port < 1 || port > 65535) {
          throw new NumberFormatException();
        }
        return new Endpoint(host, port);
      } catch (NumberFormatException error) {
        throw new HostServices.HostUnavailableException("APE_HOST has an invalid port", error);
      }
    }
  }
}
