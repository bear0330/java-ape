package com.nuwainfo.javaape;

/**
 * Public value-oriented bridge from Java APE to an explicitly configured host.
 *
 * <p>The public bridge transports only protocol values. Native fallback calls
 * additionally use the package-private, request-scoped Java environment ABI.
 */
public final class HostServices {
  private HostServices() {
  }

  /** Calls an explicitly named host capability. */
  public static Object call(String service, Object... arguments) {
    if (service == null || service.isEmpty()) {
      throw new IllegalArgumentException("host service is required");
    }

    Object[] values = normalizeArguments(arguments);
    HostInvocation invocation = HostClient.invokeService(service, values);
    invocation.applyMutations(values);
    return invocation.returnValue();
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
    return callNative(owner, name, descriptor, null, arguments);
  }

  public static Object callNative(
      String owner, String name, String descriptor, Object target, Object[] arguments) {
    if (owner == null || name == null || descriptor == null) {
      throw new IllegalArgumentException("native method identity is incomplete");
    }

    Object[] values = normalizeArguments(arguments);
    HostInvocation invocation = HostClient.invokeNative(owner, name, descriptor, target, values);
    invocation.applyMutations(values);
    invocation.throwIfException();

    return NativeReturnValue.coerce(descriptor, invocation.returnValue());
  }

  @SuppressWarnings("unchecked")
  static <T extends Throwable> void throwUnchecked(Throwable error) throws T {
    throw (T) error;
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
