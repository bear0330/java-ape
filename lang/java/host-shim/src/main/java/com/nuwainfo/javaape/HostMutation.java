package com.nuwainfo.javaape;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A host-requested replacement for one mutable primitive-array argument. */
final class HostMutation {
  private final int argument;
  private final Object value;

  private HostMutation(int argument, Object value) {
    this.argument = argument;
    this.value = value;
  }

  static List<HostMutation> decode(Object encoded, byte[] binary) {
    if (encoded == null) {
      return List.of();
    }

    List<Object> descriptors = HostValueCodec.requireList(encoded);
    List<HostMutation> mutations = new ArrayList<>(descriptors.size());
    Set<Integer> seenArguments = new HashSet<>();

    for (Object descriptor : descriptors) {
      Map<String, Object> mutation = HostValueCodec.requireMap(
          descriptor, "host mutation is not an object");
      int argument = HostValueCodec.requireInteger(
          mutation.get("argument"), "host mutation argument");

      if (argument < 0 || !seenArguments.add(argument)) {
        throw new HostServices.HostProtocolException("invalid duplicate host mutation argument");
      }

      Map<String, Object> value = HostValueCodec.requireMap(
          mutation.get("value"), "host mutation value is not an object");
      mutations.add(new HostMutation(
          argument, HostValueCodec.decodeResponseValue(value, binary)));
    }

    return mutations;
  }

  void validate(Object[] arguments) {
    if (argument >= arguments.length) {
      throw new HostServices.HostProtocolException("host mutation argument is out of range");
    }

    Object target = arguments[argument];
    if (!isPrimitiveArray(target)) {
      throw new HostServices.HostProtocolException("host mutation target is not a primitive array");
    }

    if (value == null || value.getClass() != target.getClass()) {
      throw new HostServices.HostProtocolException("host mutation type does not match its argument");
    }

    if (Array.getLength(value) != Array.getLength(target)) {
      throw new HostServices.HostProtocolException("host mutation length does not match its argument");
    }
  }

  void apply(Object[] arguments) {
    Object target = arguments[argument];
    System.arraycopy(value, 0, target, 0, Array.getLength(target));
  }

  private static boolean isPrimitiveArray(Object value) {
    return value != null && value.getClass().isArray()
        && value.getClass().getComponentType().isPrimitive();
  }
}
