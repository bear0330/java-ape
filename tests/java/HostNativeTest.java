import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Exercises the VM's descriptor-aware Java APE host-native fallback. */
public final class HostNativeTest {
  private static native int add(int left, int right);
  private static native boolean isEven(int value);
  private static native char next(char value);
  private static native String join(String prefix, long number);
  private static native byte[] reverse(byte[] value);
  private static native int[] increment(int[] value);
  private static native float multiply(float left, float right);
  private static native double divide(double left, double right);
  private static native ByteBuffer flip(ByteBuffer value);
  private static native void note(byte[] value);
  private static native void inspectTypes(Class<?> objectType, Class<?> arrayType);
  private static native long retain(byte[] profile, Object disposerReference);
  private static native void mutate(Object bytes, Object integers);
  private static native int inspectJavaException();
  private static native void throwHostIOException() throws IOException;
  private static native int objectArrayRoundTrip();
  private static native void retainGlobal(Object value);
  private static native boolean isRetainedGlobal(Object value);
  private static native void releaseGlobal();
  private native short bump(short value);

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(add(19, 23) == 42, "int result");
    check(isEven(42), "boolean result");
    check(next('a') == 'b', "char result");
    check("answer:42".equals(join("answer:", 42)), "String/long result");
    check(Arrays.equals(reverse(new byte[] {1, 2, 3}), new byte[] {3, 2, 1}), "byte[] result");
    check(Arrays.equals(increment(new int[] {7, 8}), new int[] {8, 9}), "int[] result");
    check(multiply(1.5f, 2.0f) == 3.0f, "float result");
    check(divide(7.0d, 2.0d) == 3.5d, "double result");
    ByteBuffer result = flip(ByteBuffer.wrap(new byte[] {4, 5, 6}));
    check(Arrays.equals(new byte[] {6, 5, 4}, new byte[] {result.get(), result.get(), result.get()}),
        "ByteBuffer result");
    note(new byte[] {9, 8, 7});
    inspectTypes(String.class, int[].class);
    check(retain(new byte[] {4, 3, 2, 1}, new Object()) == 1001L, "opaque reference result");
    byte[] mutableBytes = new byte[] {1, 2, 3};
    int[] mutableIntegers = new int[] {5, 6};
    mutate(mutableBytes, mutableIntegers);
    check(Arrays.equals(mutableBytes, new byte[] {3, 2, 1}), "byte[] mutation");
    check(Arrays.equals(mutableIntegers, new int[] {50, 60}), "int[] mutation through Object");
    check(inspectJavaException() == 42, "Host observed and cleared Java exception");
    check(objectArrayRoundTrip() == 42, "object-array host operations");

    Object global = new Object();
    retainGlobal(global);
    check(isRetainedGlobal(global), "global Java reference identity");
    check(!isRetainedGlobal(new Object()), "global Java reference mismatch");
    releaseGlobal();

    try {
      throwHostIOException();
      throw new AssertionError("Host exception was not thrown");
    } catch (IOException expected) {
      check("from host".equals(expected.getMessage()), "Host exception message");
    }

    check(new HostNativeTest().bump((short) 40) == 42, "instance short result");
    System.out.println("host-native fallback passed");
  }

  private static int answerAfterException() {
    return 42;
  }

  private static void throwForHost() throws IOException {
    throw new IOException("from Java callback");
  }
}
