package com.nuwainfo.javaape;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Java values to the value-oriented APE Host Services wire ABI. */
final class HostValueCodec {
  private HostValueCodec() {
  }

  static Map<String, Object> encodeRequestValue(Object value, HostReferenceArena arena) {
    if (value == null) {
      return typed("null", null);
    }
    if (value instanceof Boolean) {
      return typed("boolean", value);
    }
    if (value instanceof Byte) {
      return typed("byte", value);
    }
    if (value instanceof Short) {
      return typed("short", value);
    }
    if (value instanceof Character) {
      return typed("char", value.toString());
    }
    if (value instanceof Integer) {
      return typed("int", value);
    }
    if (value instanceof Long) {
      return typed("long", value);
    }
    if (value instanceof Float) {
      return finite("float", ((Float) value).doubleValue());
    }
    if (value instanceof Double) {
      return finite("double", (Double) value);
    }
    if (value instanceof String) {
      return typed("string", value);
    }
    if (value instanceof Class<?>) {
      return classToken((Class<?>) value, arena.add(value));
    }
    if (value instanceof byte[]) {
      return bytes((byte[]) value);
    }
    if (value instanceof ByteBuffer) {
      return bytes(copyRemaining((ByteBuffer) value));
    }
    if (value instanceof char[]) {
      return typed("char[]", new String((char[]) value));
    }

    String arrayType = primitiveArrayType(value);
    if (arrayType != null) {
      return primitiveArray(arrayType, value);
    }

    return javaReference(value, arena);
  }

  static Object decodeResponseValue(Map<String, Object> descriptor, byte[] ignoredBinary) {
    String type = requireString(descriptor.get("type"), "host value type is missing");
    Object value = descriptor.get("value");

    switch (type) {
      case "void":
      case "null":
        return null;
      case "boolean":
        return requireBoolean(value, type);
      case "byte":
        return (byte) requireNumber(value, type).intValue();
      case "short":
        return (short) requireNumber(value, type).intValue();
      case "char":
        return decodeCharacter(value);
      case "int":
        return requireNumber(value, type).intValue();
      case "long":
        return requireNumber(value, type).longValue();
      case "float":
        return requireNumber(value, type).floatValue();
      case "double":
        return requireNumber(value, type).doubleValue();
      case "string":
        return stringOrDefault(value, "");
      case "bytes":
        return decodeBytes(descriptor);
      case "boolean[]":
        return decodeBooleanArray(value);
      case "short[]":
        return decodeShortArray(value);
      case "char[]":
        return stringOrDefault(value, "").toCharArray();
      case "int[]":
        return decodeIntArray(value);
      case "long[]":
        return decodeLongArray(value);
      case "float[]":
        return decodeFloatArray(value);
      case "double[]":
        return decodeDoubleArray(value);
      default:
        throw new HostServices.HostProtocolException("unsupported host value type " + type);
    }
  }

  static Map<String, Object> requireMap(Object value, String message) {
    if (!(value instanceof Map)) {
      throw new HostServices.HostProtocolException(message);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) value;
    return result;
  }

  static List<Object> requireList(Object value) {
    if (!(value instanceof List)) {
      throw new HostServices.HostProtocolException("host array value is not an array");
    }

    @SuppressWarnings("unchecked")
    List<Object> result = (List<Object>) value;
    return result;
  }

  static Number requireNumber(Object value, String type) {
    if (!(value instanceof Number)) {
      throw new HostServices.HostProtocolException("invalid numeric " + type + " value");
    }
    return (Number) value;
  }

  static int requireInteger(Object value, String type) {
    Number number = requireNumber(value, type);
    long integer = number.longValue();
    if (number.doubleValue() != integer || integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE) {
      throw new HostServices.HostProtocolException("invalid integer " + type);
    }
    return (int) integer;
  }

  private static Map<String, Object> typed(String type, Object value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", type);
    if (value != null) {
      result.put("value", value);
    }
    return result;
  }

  private static Map<String, Object> finite(String type, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite " + type + " value");
    }
    return typed(type, value);
  }

  private static Map<String, Object> classToken(Class<?> type, long reference) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", "class");
    result.put("descriptor", descriptor(type));
    result.put("name", type.getTypeName());
    Module module = type.getModule();
    result.put("module", module == null ? null : module.getName());
    result.put("loader", loaderKind(type));
    result.put("loaderId", loaderId(type));
    result.put("ref", reference);
    return result;
  }

  private static Map<String, Object> javaReference(Object value, HostReferenceArena arena) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", "java-ref");
    result.put("id", arena.add(value));
    result.put("class", classToken(value.getClass(), arena.add(value.getClass())));
    return result;
  }

  private static Map<String, Object> bytes(byte[] value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", "bytes");
    result.put("encoding", "base64");
    result.put("data", Base64.getEncoder().encodeToString(value));
    return result;
  }

  private static byte[] copyRemaining(ByteBuffer source) {
    ByteBuffer copy = source.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  private static String primitiveArrayType(Object value) {
    if (value instanceof boolean[]) {
      return "boolean[]";
    }
    if (value instanceof short[]) {
      return "short[]";
    }
    if (value instanceof int[]) {
      return "int[]";
    }
    if (value instanceof long[]) {
      return "long[]";
    }
    if (value instanceof float[]) {
      return "float[]";
    }
    if (value instanceof double[]) {
      return "double[]";
    }
    return null;
  }

  private static Map<String, Object> primitiveArray(String type, Object values) {
    List<Object> result = new ArrayList<>();
    int length = Array.getLength(values);
    for (int index = 0; index < length; ++index) {
      Object value = Array.get(values, index);
      if (value instanceof Float && !Float.isFinite((Float) value)) {
        throw new IllegalArgumentException("non-finite " + type + " value");
      }
      if (value instanceof Double && !Double.isFinite((Double) value)) {
        throw new IllegalArgumentException("non-finite " + type + " value");
      }
      result.add(value);
    }
    return typed(type, result);
  }

  private static String descriptor(Class<?> type) {
    if (type.isArray()) {
      return type.getName().replace('.', '/');
    }
    if (!type.isPrimitive()) {
      return "L" + type.getName().replace('.', '/') + ";";
    }
    if (type == void.class) {
      return "V";
    }
    if (type == boolean.class) {
      return "Z";
    }
    if (type == byte.class) {
      return "B";
    }
    if (type == char.class) {
      return "C";
    }
    if (type == short.class) {
      return "S";
    }
    if (type == int.class) {
      return "I";
    }
    if (type == long.class) {
      return "J";
    }
    if (type == float.class) {
      return "F";
    }
    if (type == double.class) {
      return "D";
    }
    throw new IllegalArgumentException("unknown primitive class " + type.getName());
  }

  private static String loaderKind(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    if (loader == null) {
      return "bootstrap";
    }
    if (loader == ClassLoader.getPlatformClassLoader()) {
      return "platform";
    }
    if (loader == ClassLoader.getSystemClassLoader()) {
      return "application";
    }
    return "custom";
  }

  private static long loaderId(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    return loader == null ? 0 : Integer.toUnsignedLong(System.identityHashCode(loader));
  }

  private static Boolean requireBoolean(Object value, String type) {
    if (!(value instanceof Boolean)) {
      throw new HostServices.HostProtocolException("invalid " + type + " value");
    }
    return (Boolean) value;
  }

  private static String requireString(Object value, String message) {
    if (!(value instanceof String)) {
      throw new HostServices.HostProtocolException(message);
    }
    return (String) value;
  }

  private static String stringOrDefault(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    return requireString(value, "invalid string value");
  }

  private static char decodeCharacter(Object value) {
    String character = requireString(value, "invalid char value");
    if (character.length() != 1) {
      throw new HostServices.HostProtocolException("invalid char value");
    }
    return character.charAt(0);
  }

  private static byte[] decodeBytes(Map<String, Object> descriptor) {
    String encoding = requireString(descriptor.get("encoding"), "missing bytes encoding");
    String data = requireString(descriptor.get("data"), "missing base64 bytes");
    if (!"base64".equals(encoding)) {
      throw new HostServices.HostProtocolException("unsupported bytes encoding " + encoding);
    }
    try {
      return Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException error) {
      throw new HostServices.HostProtocolException("invalid base64 bytes", error);
    }
  }

  private static boolean[] decodeBooleanArray(Object value) {
    List<Object> values = requireList(value);
    boolean[] result = new boolean[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireBoolean(values.get(index), "boolean[]");
    }
    return result;
  }

  private static short[] decodeShortArray(Object value) {
    List<Object> values = requireList(value);
    short[] result = new short[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireNumber(values.get(index), "short[]").shortValue();
    }
    return result;
  }

  private static int[] decodeIntArray(Object value) {
    List<Object> values = requireList(value);
    int[] result = new int[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireNumber(values.get(index), "int[]").intValue();
    }
    return result;
  }

  private static long[] decodeLongArray(Object value) {
    List<Object> values = requireList(value);
    long[] result = new long[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireNumber(values.get(index), "long[]").longValue();
    }
    return result;
  }

  private static float[] decodeFloatArray(Object value) {
    List<Object> values = requireList(value);
    float[] result = new float[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireNumber(values.get(index), "float[]").floatValue();
    }
    return result;
  }

  private static double[] decodeDoubleArray(Object value) {
    List<Object> values = requireList(value);
    double[] result = new double[values.size()];
    for (int index = 0; index < result.length; ++index) {
      result[index] = requireNumber(values.get(index), "double[]").doubleValue();
    }
    return result;
  }
}
