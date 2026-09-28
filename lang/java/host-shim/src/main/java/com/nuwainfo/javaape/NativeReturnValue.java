package com.nuwainfo.javaape;

import java.nio.ByteBuffer;

final class NativeReturnValue {
  private NativeReturnValue() {
  }

  static Object coerce(String descriptor, Object value) {
    int returnIndex = descriptor.indexOf(')') + 1;
    if (returnIndex == 0 || returnIndex >= descriptor.length()) {
      throw new HostServices.HostProtocolException("invalid native descriptor: " + descriptor);
    }

    char type = descriptor.charAt(returnIndex);
    if (type == 'V') {
      return null;
    }

    if (value == null) {
      return coerceNull(type);
    }

    switch (type) {
      case 'Z':
        return require(value, Boolean.class);
      case 'B':
        return require(value, Byte.class);
      case 'C':
        return require(value, Character.class);
      case 'S':
        return require(value, Short.class);
      case 'I':
        return require(value, Integer.class);
      case 'J':
        return require(value, Long.class);
      case 'F':
        return require(value, Float.class);
      case 'D':
        return require(value, Double.class);
      case '[':
        return coerceArray(descriptor, returnIndex, value);
      case 'L':
        return coerceObject(descriptor, returnIndex, value);
      default:
        throw new HostServices.HostProtocolException("invalid native return type " + type);
    }
  }

  private static Object coerceNull(char type) {
    if (type == 'L' || type == '[') {
      return null;
    }

    throw new HostServices.HostProtocolException("primitive native method returned null");
  }

  private static Object coerceArray(String descriptor, int returnIndex, Object value) {
    String returnType = descriptor.substring(returnIndex);

    switch (returnType) {
      case "[Z":
        return require(value, boolean[].class);
      case "[B":
        return require(value, byte[].class);
      case "[S":
        return require(value, short[].class);
      case "[C":
        return require(value, char[].class);
      case "[I":
        return require(value, int[].class);
      case "[J":
        return require(value, long[].class);
      case "[F":
        return require(value, float[].class);
      case "[D":
        return require(value, double[].class);
      default:
        throw new HostServices.HostProtocolException(
            "unsupported host-native array return " + returnType);
    }
  }

  private static Object coerceObject(String descriptor, int returnIndex, Object value) {
    String returnType = descriptor.substring(returnIndex);

    if ("Ljava/lang/String;".equals(returnType)) {
      return require(value, String.class);
    }

    if ("Ljava/nio/ByteBuffer;".equals(returnType)) {
      return ByteBuffer.wrap(require(value, byte[].class));
    }

    throw new HostServices.HostProtocolException(
        "unsupported host-native object return " + returnType);
  }

  private static <T> T require(Object value, Class<T> type) {
    if (!type.isInstance(value)) {
      throw new HostServices.HostProtocolException(
          "expected " + type.getName() + " but host returned " + value.getClass().getName());
    }

    return type.cast(value);
  }
}
