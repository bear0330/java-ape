package com.nuwainfo.javaape;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

final class HostValueCodec {
  private HostValueCodec() {
  }

  static void appendRequestValue(StringBuilder json, ByteArrayOutputStream binary, Object value) {
    if (value == null) {
      json.append("{\"type\":\"null\"}");
      return;
    }

    if (value instanceof Boolean) {
      appendBoolean(json, (Boolean) value);
      return;
    }

    if (value instanceof Byte) {
      appendNumber(json, "byte", value);
      return;
    }

    if (value instanceof Short) {
      appendNumber(json, "short", value);
      return;
    }

    if (value instanceof Character) {
      appendCharacter(json, (Character) value);
      return;
    }

    if (value instanceof Integer) {
      appendNumber(json, "int", value);
      return;
    }

    if (value instanceof Long) {
      appendNumber(json, "long", value);
      return;
    }

    if (value instanceof Float) {
      appendFiniteNumber(json, "float", ((Float) value).doubleValue());
      return;
    }

    if (value instanceof Double) {
      appendFiniteNumber(json, "double", (Double) value);
      return;
    }

    if (value instanceof String) {
      appendTypedString(json, "string", (String) value);
      return;
    }

    if (value instanceof byte[]) {
      appendBytes(json, binary, (byte[]) value);
      return;
    }

    if (value instanceof ByteBuffer) {
      appendBytes(json, binary, copyRemaining((ByteBuffer) value));
      return;
    }

    if (value instanceof char[]) {
      appendTypedString(json, "char[]", new String((char[]) value));
      return;
    }

    String arrayType = primitiveArrayType(value);
    if (arrayType != null) {
      appendPrimitiveArray(json, arrayType, value);
      return;
    }

    throw new IllegalArgumentException(
        "unsupported Java APE host value: " + value.getClass().getName());
  }

  static Object decodeResponseValue(Map<String, Object> descriptor, byte[] binary) {
    String type = requireString(descriptor.get("type"), "host return type is missing");
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
        return decodeBytes(descriptor, binary);
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
        throw new HostServices.HostProtocolException("unsupported host return type " + type);
    }
  }

  static Map<String, Object> requireMap(Object value, String message) {
    if (!(value instanceof Map)) {
      throw new HostServices.HostProtocolException(message);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) value;
    return map;
  }

  static void appendString(StringBuilder output, String value) {
    output.append('"');

    for (int index = 0; index < value.length(); ++index) {
      appendEscapedCharacter(output, value.charAt(index));
    }

    output.append('"');
  }

  private static void appendBoolean(StringBuilder json, Boolean value) {
    json.append("{\"type\":\"boolean\",\"value\":").append(value).append('}');
  }

  private static void appendNumber(StringBuilder json, String type, Object value) {
    json.append("{\"type\":\"").append(type).append("\",\"value\":")
        .append(value).append('}');
  }

  private static void appendCharacter(StringBuilder json, Character value) {
    json.append("{\"type\":\"char\",\"value\":");
    appendString(json, value.toString());
    json.append('}');
  }

  private static void appendTypedString(StringBuilder json, String type, String value) {
    json.append("{\"type\":\"").append(type).append("\",\"value\":");
    appendString(json, value);
    json.append('}');
  }

  private static void appendFiniteNumber(StringBuilder json, String type, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite " + type + " value");
    }

    json.append("{\"type\":\"").append(type).append("\",\"value\":")
        .append(value).append('}');
  }

  private static void appendBytes(StringBuilder json, ByteArrayOutputStream binary, byte[] bytes) {
    int offset = binary.size();
    binary.write(bytes, 0, bytes.length);
    json.append("{\"type\":\"bytes\",\"offset\":").append(offset)
        .append(",\"length\":").append(bytes.length).append('}');
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

  private static void appendPrimitiveArray(StringBuilder json, String type, Object values) {
    json.append("{\"type\":\"").append(type).append("\",\"value\":[");

    int length = Array.getLength(values);
    for (int index = 0; index < length; ++index) {
      if (index != 0) {
        json.append(',');
      }

      Object value = Array.get(values, index);
      appendArrayElement(json, type, value);
    }

    json.append("]}");
  }

  private static void appendArrayElement(StringBuilder json, String type, Object value) {
    if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) {
        throw new IllegalArgumentException("non-finite " + type + " value");
      }
    }

    json.append(value);
  }

  private static void appendEscapedCharacter(StringBuilder output, char value) {
    switch (value) {
      case '"':
        output.append("\\\"");
        return;
      case '\\':
        output.append("\\\\");
        return;
      case '\b':
        output.append("\\b");
        return;
      case '\f':
        output.append("\\f");
        return;
      case '\n':
        output.append("\\n");
        return;
      case '\r':
        output.append("\\r");
        return;
      case '\t':
        output.append("\\t");
        return;
      default:
        appendUnescapedCharacter(output, value);
    }
  }

  private static void appendUnescapedCharacter(StringBuilder output, char value) {
    if (value < 0x20) {
      output.append(String.format("\\u%04x", (int) value));
      return;
    }

    output.append(value);
  }

  private static char decodeCharacter(Object value) {
    String character = requireString(value, "invalid char return");
    if (character.length() != 1) {
      throw new HostServices.HostProtocolException("invalid char return");
    }

    return character.charAt(0);
  }

  private static byte[] decodeBytes(Map<String, Object> descriptor, byte[] binary) {
    int offset = requireNumber(descriptor.get("offset"), "offset").intValue();
    int length = requireNumber(descriptor.get("length"), "length").intValue();
    if (offset < 0 || length < 0 || offset > binary.length - length) {
      throw new HostServices.HostProtocolException("invalid binary return range");
    }

    byte[] bytes = new byte[length];
    System.arraycopy(binary, offset, bytes, 0, length);
    return bytes;
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

  private static Boolean requireBoolean(Object value, String type) {
    if (!(value instanceof Boolean)) {
      throw new HostServices.HostProtocolException("invalid " + type + " return");
    }

    return (Boolean) value;
  }

  private static Number requireNumber(Object value, String type) {
    if (!(value instanceof Number)) {
      throw new HostServices.HostProtocolException("invalid numeric " + type + " return");
    }

    return (Number) value;
  }

  private static String requireString(Object value, String message) {
    if (!(value instanceof String)) {
      throw new HostServices.HostProtocolException(message);
    }

    return (String) value;
  }

  private static String stringOrDefault(Object value, String fallback) {
    if (value instanceof String) {
      return (String) value;
    }

    return fallback;
  }

  private static List<Object> requireList(Object value) {
    if (!(value instanceof List)) {
      throw new HostServices.HostProtocolException("array return is not an array");
    }

    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    return list;
  }
}
