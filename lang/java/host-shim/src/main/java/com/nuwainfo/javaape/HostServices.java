package com.nuwainfo.javaape;

/**
 * Public value-oriented bridge from Java APE to an explicitly configured host.
 *
 * <p>The bridge transports only protocol values. It is not remote JNI and does
 * not expose remote object references, callbacks, or class loading.
 */
public final class HostServices {
  private HostServices() {
  }

  /** Calls an explicitly named host capability. */
  public static Object call(String service, Object... arguments) {
    if (service == null || service.isEmpty()) {
      throw new IllegalArgumentException("host service is required");
    }

    return HostClient.invoke(service, normalizeArguments(arguments));
  }

  /** Convenience API for the common byte-to-byte host capability shape. */
  public static byte[] callBytes(String service, byte[] value) {
    Object result = call(service, value);
    if (!(result instanceof byte[])) {
      throw new HostProtocolException("host service " + service + " did not return bytes");
    }

    return (byte[]) result;
  }

  /**
   * Entry used by the VM's descriptor-aware native fallback.
   *
   * <p>The service identity is {@code owner.name(descriptor)}.
   */
  public static Object callNative(String owner, String name, String descriptor, Object[] arguments) {
    if (owner == null || name == null || descriptor == null) {
      throw new IllegalArgumentException("native method identity is incomplete");
    }

    String service = owner.replace('/', '.') + "." + name + descriptor;
    Object result = HostClient.invoke(service, normalizeArguments(arguments));

    return NativeReturnValue.coerce(descriptor, result);
  }

  private static Object[] normalizeArguments(Object[] arguments) {
    if (arguments == null) {
      return new Object[0];
    }

    return arguments;
  }

  public static class HostUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HostUnavailableException(String message) {
      super(message);
    }

    public HostUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class HostProtocolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HostProtocolException(String message) {
      super(message);
    }

    public HostProtocolException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
