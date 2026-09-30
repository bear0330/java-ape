package com.nuwainfo.javaape;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes ordered, request-scoped Java environment operations for a host peer. */
final class JavaEnvironment {
  private JavaEnvironment() {
  }

  static Map<String, Object> dispatch(
      String method, Map<String, Object> params, HostReferenceArena arena) {
    verifyCall(params, arena);

    if ("java.env.execute".equals(method)) {
      return execute(params, arena);
    }

    if ("java.ref.release".equals(method)) {
      return release(params, arena);
    }

    throw new HostServices.HostProtocolException("unsupported host request " + method);
  }

  private static Map<String, Object> execute(Map<String, Object> params, HostReferenceArena arena) {
    List<Object> encoded = HostValueCodec.requireList(params.get("ops"));
    Map<String, Object> temporaries = new LinkedHashMap<>();
    Map<String, Object> values = new LinkedHashMap<>();
    ExceptionState exceptions = new ExceptionState();

    for (Object entry : encoded) {
      Map<String, Object> operation = HostValueCodec.requireMap(entry, "java.env operation is not an object");
      String name = requireString(operation.get("op"), "java.env operation name is missing");
      if (exceptions.isPending() && !isExceptionOperation(name)) {
        throw new HostServices.HostProtocolException(
            "java.env has a pending exception; check, clear, or return it first");
      }

      Object value = executeOperation(name, operation, temporaries, arena, exceptions);
      Object output = operation.get("out");

      if (output != null) {
        String key = requireString(output, "java.env output name is not a string");
        if (temporaries.put(key, value) != null) {
          throw new HostServices.HostProtocolException("duplicate java.env output " + key);
        }
        values.put(key, encodeValue(value, arena));
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("callId", arena.callId());
    result.put("values", values);
    if (exceptions.isPending()) {
      result.put("javaException", encodeValue(exceptions.current(), arena));
    }
    return result;
  }

  private static Map<String, Object> release(Map<String, Object> params, HostReferenceArena arena) {
    List<Object> references = HostValueCodec.requireList(params.get("refs"));

    for (Object entry : references) {
      arena.release(HostValueCodec.requireInteger(entry, "Java reference ID"));
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("callId", arena.callId());
    result.put("released", references.size());
    return result;
  }

  private static Object executeOperation(
      String name,
      Map<String, Object> operation,
      Map<String, Object> temporaries,
      HostReferenceArena arena,
      ExceptionState exceptions) {
    switch (name) {
      case "findClass":
        return findClass(requireString(operation.get("name"), "class name is missing"));
      case "getObjectClass":
        return getObjectClass(operation, temporaries, arena);
      case "isInstanceOf":
        return isInstanceOf(operation, temporaries, arena);
      case "isSameObject":
        return isSameObject(operation, temporaries, arena);
      case "getMethod":
        return findMethod(operation, temporaries, arena);
      case "getField":
        return findField(operation, temporaries, arena);
      case "newObject":
        return newObject(operation, temporaries, arena, exceptions);
      case "newObjectArray":
        return newObjectArray(operation, temporaries, arena);
      case "getObjectArrayElement":
        return getObjectArrayElement(operation, temporaries, arena);
      case "setObjectArrayElement":
        setObjectArrayElement(operation, temporaries, arena);
        return null;
      case "getArrayLength":
        return getArrayLength(operation, temporaries, arena);
      case "newGlobalRef":
        return newGlobalRef(operation, temporaries, arena);
      case "deleteGlobalRef":
        deleteGlobalRef(operation);
        return null;
      case "call":
        return call(operation, temporaries, arena, false, exceptions);
      case "callStatic":
        return call(operation, temporaries, arena, true, exceptions);
      case "readField":
        return readField(operation, temporaries, arena, false);
      case "readStaticField":
        return readField(operation, temporaries, arena, true);
      case "writeField":
        writeField(operation, temporaries, arena, false);
        return null;
      case "writeStaticField":
        writeField(operation, temporaries, arena, true);
        return null;
      case "throw":
        exceptions.throwValue(resolve(operation.get("throwable"), temporaries, arena));
        return null;
      case "throwNew":
        exceptions.throwNew(operation, temporaries, arena);
        return null;
      case "exceptionCheck":
        return exceptions.isPending();
      case "exceptionOccurred":
        return exceptions.current();
      case "exceptionClear":
        exceptions.clear();
        return null;
      default:
        throw new HostServices.HostProtocolException("unsupported java.env operation " + name);
    }
  }

  private static boolean isExceptionOperation(String name) {
    return "throw".equals(name)
        || "throwNew".equals(name)
        || "exceptionCheck".equals(name)
        || "exceptionOccurred".equals(name)
        || "exceptionClear".equals(name);
  }

  private static Class<?> getObjectClass(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object value = resolve(operation.get("value"), temporaries, arena);
    return value == null ? null : value.getClass();
  }

  private static boolean isInstanceOf(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object value = resolve(operation.get("value"), temporaries, arena);
    Class<?> type = requireClass(resolve(operation.get("class"), temporaries, arena));
    return value != null && type.isInstance(value);
  }

  private static boolean isSameObject(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object left = resolve(operation.get("left"), temporaries, arena);
    Object right = resolve(operation.get("right"), temporaries, arena);
    return left == right;
  }

  private static Object newObjectArray(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Class<?> component = requireClass(resolve(operation.get("component"), temporaries, arena));
    int length = HostValueCodec.requireInteger(operation.get("length"), "Java object-array length");
    if (component.isPrimitive()) {
      throw new HostServices.HostProtocolException("Java object-array component cannot be primitive");
    }
    if (length < 0) {
      throw new HostServices.HostProtocolException("Java object-array length cannot be negative");
    }

    Object result = Array.newInstance(component, length);
    if (operation.containsKey("initial")) {
      Object initial = resolve(operation.get("initial"), temporaries, arena);
      for (int index = 0; index < length; ++index) {
        Array.set(result, index, initial);
      }
    }
    return result;
  }

  private static Object getObjectArrayElement(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object array = requireObjectArray(resolve(operation.get("array"), temporaries, arena));
    int index = HostValueCodec.requireInteger(operation.get("index"), "Java object-array index");
    try {
      return Array.get(array, index);
    } catch (ArrayIndexOutOfBoundsException error) {
      throw new HostServices.HostProtocolException("Java object-array index is out of range", error);
    }
  }

  private static void setObjectArrayElement(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object array = requireObjectArray(resolve(operation.get("array"), temporaries, arena));
    int index = HostValueCodec.requireInteger(operation.get("index"), "Java object-array index");
    Object value = resolve(operation.get("value"), temporaries, arena);
    try {
      Array.set(array, index, value);
    } catch (ArrayIndexOutOfBoundsException error) {
      throw new HostServices.HostProtocolException("Java object-array index is out of range", error);
    } catch (IllegalArgumentException error) {
      throw new HostServices.HostProtocolException("Java object-array value has the wrong type", error);
    }
  }

  private static int getArrayLength(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object array = resolve(operation.get("array"), temporaries, arena);
    try {
      return Array.getLength(array);
    } catch (IllegalArgumentException error) {
      throw new HostServices.HostProtocolException("java.env value is not an array", error);
    }
  }

  private static HostGlobalReference newGlobalRef(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    return HostGlobalReferences.add(resolve(operation.get("value"), temporaries, arena));
  }

  private static void deleteGlobalRef(Map<String, Object> operation) {
    HostGlobalReferences.release(globalReference(operation.get("ref")));
  }

  private static Class<?> findClass(String name) {
    String binaryName = name.replace('/', '.');
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    try {
      if (loader != null) {
        return Class.forName(binaryName, false, loader);
      }
      return Class.forName(binaryName, false, null);
    } catch (ClassNotFoundException ignored) {
      try {
        return Class.forName(binaryName, false, null);
      } catch (ClassNotFoundException error) {
        throw new HostServices.HostProtocolException("cannot find Java class " + name, error);
      }
    }
  }

  private static Executable findMethod(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Class<?> type = requireClass(resolve(operation.get("class"), temporaries, arena));
    String name = requireString(operation.get("name"), "method name is missing");
    String descriptor = requireString(operation.get("descriptor"), "method descriptor is missing");

    if ("<init>".equals(name)) {
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (descriptor.equals(descriptor(constructor))) {
          return constructor;
        }
      }
    } else {
      for (Method method : type.getDeclaredMethods()) {
        if (name.equals(method.getName()) && descriptor.equals(descriptor(method))) {
          return method;
        }
      }

      for (Method method : type.getMethods()) {
        if (name.equals(method.getName()) && descriptor.equals(descriptor(method))) {
          return method;
        }
      }
    }

    throw new HostServices.HostProtocolException("cannot find Java method " + type.getName() + "." + name);
  }

  private static Field findField(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Class<?> type = requireClass(resolve(operation.get("class"), temporaries, arena));
    String name = requireString(operation.get("name"), "field name is missing");

    try {
      return type.getDeclaredField(name);
    } catch (NoSuchFieldException ignored) {
      try {
        return type.getField(name);
      } catch (NoSuchFieldException error) {
        throw new HostServices.HostProtocolException("cannot find Java field " + type.getName() + "." + name, error);
      }
    }
  }

  private static Object newObject(
      Map<String, Object> operation,
      Map<String, Object> temporaries,
      HostReferenceArena arena,
      ExceptionState exceptions) {
    Object member = resolve(operation.get("method"), temporaries, arena);
    if (!(member instanceof Constructor)) {
      throw new HostServices.HostProtocolException("java.env newObject method is not a constructor");
    }

    Constructor<?> constructor = (Constructor<?>) member;
    try {
      makeAccessible(constructor);
      return constructor.newInstance(arguments(operation, temporaries, arena));
    } catch (InvocationTargetException error) {
      exceptions.throwValue(error.getCause());
      return null;
    } catch (ReflectiveOperationException error) {
      throw invocationFailure("construct Java object", error);
    }
  }

  private static Object call(
      Map<String, Object> operation,
      Map<String, Object> temporaries,
      HostReferenceArena arena,
      boolean expectedStatic,
      ExceptionState exceptions) {
    Object member = resolve(operation.get("method"), temporaries, arena);
    if (!(member instanceof Method)) {
      throw new HostServices.HostProtocolException("java.env call method is not a Method");
    }

    Method method = (Method) member;
    boolean isStatic = Modifier.isStatic(method.getModifiers());
    if (isStatic != expectedStatic) {
      throw new HostServices.HostProtocolException("java.env call has the wrong static mode");
    }

    Object target = null;
    if (!isStatic) {
      target = resolve(operation.get("target"), temporaries, arena);
    }

    try {
      makeAccessible(method);
      return method.invoke(target, arguments(operation, temporaries, arena));
    } catch (InvocationTargetException error) {
      exceptions.throwValue(error.getCause());
      return null;
    } catch (ReflectiveOperationException error) {
      throw invocationFailure("call Java method", error);
    }
  }

  private static Object readField(
      Map<String, Object> operation,
      Map<String, Object> temporaries,
      HostReferenceArena arena,
      boolean expectedStatic) {
    Field field = requireField(resolve(operation.get("field"), temporaries, arena));
    boolean isStatic = Modifier.isStatic(field.getModifiers());
    if (isStatic != expectedStatic) {
      throw new HostServices.HostProtocolException("java.env field read has the wrong static mode");
    }

    try {
      makeAccessible(field);
      return field.get(isStatic ? null : resolve(operation.get("target"), temporaries, arena));
    } catch (IllegalAccessException error) {
      throw invocationFailure("read Java field", error);
    }
  }

  private static void writeField(
      Map<String, Object> operation,
      Map<String, Object> temporaries,
      HostReferenceArena arena,
      boolean expectedStatic) {
    Field field = requireField(resolve(operation.get("field"), temporaries, arena));
    boolean isStatic = Modifier.isStatic(field.getModifiers());
    if (isStatic != expectedStatic) {
      throw new HostServices.HostProtocolException("java.env field write has the wrong static mode");
    }

    try {
      makeAccessible(field);
      field.set(isStatic ? null : resolve(operation.get("target"), temporaries, arena),
          resolve(operation.get("value"), temporaries, arena));
    } catch (IllegalAccessException error) {
      throw invocationFailure("write Java field", error);
    }
  }

  private static Object[] arguments(
      Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
    Object encoded = operation.get("arguments");
    if (encoded == null) {
      return new Object[0];
    }

    List<Object> values = HostValueCodec.requireList(encoded);
    Object[] result = new Object[values.size()];

    for (int index = 0; index < result.length; ++index) {
      result[index] = resolve(values.get(index), temporaries, arena);
    }

    return result;
  }

  private static Object resolve(Object encoded, Map<String, Object> temporaries, HostReferenceArena arena) {
    if (!(encoded instanceof Map)) {
      return encoded;
    }

    Map<String, Object> value = HostValueCodec.requireMap(encoded, "java.env value is not an object");
    if (value.containsKey("tmp")) {
      String name = requireString(value.get("tmp"), "java.env temporary name is not a string");
      if (!temporaries.containsKey(name)) {
        throw new HostServices.HostProtocolException("unknown java.env temporary " + name);
      }
      return temporaries.get(name);
    }

    if (value.containsKey("ref")) {
      return arena.require(HostValueCodec.requireInteger(value.get("ref"), "Java reference ID"));
    }

    if (value.containsKey("globalRef")) {
      return HostGlobalReferences.require(globalReference(value));
    }

    if (value.containsKey("type")) {
      if ("global-ref".equals(value.get("type"))) {
        return HostGlobalReferences.require(globalReference(value));
      }
      return HostValueCodec.decodeResponseValue(value, new byte[0]);
    }

    throw new HostServices.HostProtocolException("invalid java.env value");
  }

  private static Map<String, Object> encodeValue(Object value, HostReferenceArena arena) {
    if (value instanceof HostGlobalReference) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("type", "global-ref");
      result.put("id", ((HostGlobalReference) value).identifier());
      return result;
    }

    return HostValueCodec.encodeRequestValue(value, arena);
  }

  private static long globalReference(Object encoded) {
    Map<String, Object> value = HostValueCodec.requireMap(
        encoded, "global Java reference is not an object");
    Object identifier = value.containsKey("globalRef") ? value.get("globalRef") : value.get("id");
    return HostValueCodec.requireInteger(identifier, "global Java reference ID");
  }

  private static void verifyCall(Map<String, Object> params, HostReferenceArena arena) {
    int callId = HostValueCodec.requireInteger(params.get("callId"), "java.env call ID");
    if (callId != arena.callId()) {
      throw new HostServices.HostProtocolException("java.env call ID does not match active native call");
    }
  }

  private static Class<?> requireClass(Object value) {
    if (!(value instanceof Class)) {
      throw new HostServices.HostProtocolException("java.env value is not a Class");
    }
    return (Class<?>) value;
  }

  private static Field requireField(Object value) {
    if (!(value instanceof Field)) {
      throw new HostServices.HostProtocolException("java.env value is not a Field");
    }
    return (Field) value;
  }

  private static Object requireObjectArray(Object value) {
    if (value == null || !value.getClass().isArray() || value.getClass().getComponentType().isPrimitive()) {
      throw new HostServices.HostProtocolException("java.env value is not an object array");
    }
    return value;
  }

  private static String requireString(Object value, String message) {
    if (!(value instanceof String)) {
      throw new HostServices.HostProtocolException(message);
    }
    return (String) value;
  }

  private static void makeAccessible(java.lang.reflect.AccessibleObject member) {
    if (!member.trySetAccessible()) {
      throw new HostServices.HostProtocolException("Java member is not accessible to Host Services");
    }
  }

  private static String descriptor(Executable executable) {
    StringBuilder result = new StringBuilder();
    result.append('(');
    for (Parameter parameter : executable.getParameters()) {
      result.append(descriptor(parameter.getType()));
    }
    result.append(')');
    if (executable instanceof Method) {
      result.append(descriptor(((Method) executable).getReturnType()));
    } else {
      result.append('V');
    }
    return result.toString();
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
    throw new HostServices.HostProtocolException("unknown Java primitive type " + type);
  }

  private static HostServices.HostProtocolException invocationFailure(
      String action, ReflectiveOperationException error) {
    Throwable cause = error instanceof InvocationTargetException
        ? ((InvocationTargetException) error).getCause() : error;
    return new HostServices.HostProtocolException(action + " failed: " + cause, cause);
  }

  private static final class ExceptionState {
    private Throwable current;

    private boolean isPending() {
      return current != null;
    }

    private Throwable current() {
      return current;
    }

    private void clear() {
      current = null;
    }

    private void throwValue(Object value) {
      if (!(value instanceof Throwable)) {
        throw new HostServices.HostProtocolException("java.env throwable is not a Throwable");
      }
      if (current != null) {
        throw new HostServices.HostProtocolException("java.env already has a pending exception");
      }
      current = (Throwable) value;
    }

    private void throwNew(
        Map<String, Object> operation, Map<String, Object> temporaries, HostReferenceArena arena) {
      Class<?> type = requireClass(resolve(operation.get("class"), temporaries, arena));
      if (!Throwable.class.isAssignableFrom(type)) {
        throw new HostServices.HostProtocolException("java.env throwNew class is not a Throwable");
      }

      String message = requireString(operation.get("message"), "java.env throwNew message is missing");
      try {
        Constructor<?> constructor = type.getDeclaredConstructor(String.class);
        makeAccessible(constructor);
        throwValue(constructor.newInstance(message));
      } catch (ReflectiveOperationException error) {
        throw invocationFailure("construct Java exception", error);
      }
    }
  }
}
