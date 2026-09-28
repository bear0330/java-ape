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
    check(new HostNativeTest().bump((short) 40) == 42, "instance short result");
    System.out.println("host-native fallback passed");
  }
}
