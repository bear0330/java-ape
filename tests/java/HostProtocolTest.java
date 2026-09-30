package com.nuwainfo.javaape;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tests Java value conversion independently of the LSP4J transport. */
public final class HostProtocolTest {
  private HostProtocolTest() {
  }

  public static void main(String[] arguments) {
    verifyReferenceValues();
    verifyPrimitiveArrayMutations();
    verifyRejectedMutationsAreAtomic();
    System.out.println("host protocol values passed");
  }

  private static void verifyReferenceValues() {
    Object opaque = new Object();
    HostReferenceArena arena = new HostReferenceArena(42);
    Map<String, Object> stringClass = HostValueCodec.encodeRequestValue(String.class, arena);
    Map<String, Object> firstReference = HostValueCodec.encodeRequestValue(opaque, arena);
    Map<String, Object> secondReference = HostValueCodec.encodeRequestValue(opaque, arena);
    Map<String, Object> bytes = HostValueCodec.encodeRequestValue(new byte[] {1, 2, 3}, arena);

    check("class".equals(stringClass.get("type")), "class token type");
    check("Ljava/lang/String;".equals(stringClass.get("descriptor")), "class descriptor");
    check(Long.valueOf(1).equals(stringClass.get("ref")), "class reference");
    check("java-ref".equals(firstReference.get("type")), "Java reference type");
    check(firstReference.get("id").equals(secondReference.get("id")), "Java reference identity");
    check("base64".equals(bytes.get("encoding")), "bytes encoding");
    check("AQID".equals(bytes.get("data")), "bytes payload");
  }

  private static void verifyPrimitiveArrayMutations() {
    byte[] output = new byte[] {1, 2, 3};
    int[] counters = new int[] {5, 6};
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("return", typed("void", null));
    response.put("mutations", Arrays.asList(
        mutation(0, bytes("AwIB")),
        mutation(1, typed("int[]", Arrays.asList(50, 60)))));
    HostInvocation invocation = HostResponse.invocation(response, new HostReferenceArena(43));
    invocation.applyMutations(new Object[] {output, counters});

    check(Arrays.equals(output, new byte[] {3, 2, 1}), "byte array mutation");
    check(Arrays.equals(counters, new int[] {50, 60}), "int array mutation");
  }

  private static void verifyRejectedMutationsAreAtomic() {
    byte[] output = new byte[] {1, 2, 3};
    int[] counters = new int[] {5, 6};
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("mutations", Arrays.asList(
        mutation(0, bytes("AwIB")),
        mutation(1, typed("int[]", Arrays.asList(50)))));
    HostInvocation invocation = HostResponse.invocation(response, new HostReferenceArena(43));

    try {
      invocation.applyMutations(new Object[] {output, counters});
      throw new AssertionError("mismatched mutation length was accepted");
    } catch (HostServices.HostProtocolException expected) {
      // Every mutation is validated before any destination array is modified.
    }

    check(Arrays.equals(output, new byte[] {1, 2, 3}), "rejected byte mutation was applied");
    check(Arrays.equals(counters, new int[] {5, 6}), "rejected int mutation was applied");
  }

  private static Map<String, Object> mutation(int argument, Map<String, Object> value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("argument", argument);
    result.put("value", value);
    return result;
  }

  private static Map<String, Object> typed(String type, Object value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", type);
    if (value != null) {
      result.put("value", value);
    }
    return result;
  }

  private static Map<String, Object> bytes(String data) {
    Map<String, Object> result = typed("bytes", null);
    result.put("encoding", "base64");
    result.put("data", data);
    return result;
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
