import com.nuwainfo.javaape.HostServices;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Exercises the fixed boot-loaded Java shim API without any JNI binding. */
public final class HostShimTest {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public static void main(String[] args) {
    check("HELLO".equals(HostServices.call("shim.upper", "hello")), "string shim result");
    check(Arrays.equals(HostServices.callBytes("shim.reverse", new byte[] {2, 4, 6}),
                        new byte[] {6, 4, 2}), "bytes shim result");
    Object numbers = HostServices.call("shim.increment", new int[] {10, 11});
    check(numbers instanceof int[] && Arrays.equals((int[]) numbers, new int[] {11, 12}),
        "primitive array shim result");
    Object copied = HostServices.call("shim.reverse", ByteBuffer.wrap(new byte[] {1, 3, 5}));
    check(copied instanceof byte[] && Arrays.equals((byte[]) copied, new byte[] {5, 3, 1}),
        "ByteBuffer copy-mode shim result");
    Object nativeResult = HostServices.callNative(
        "HostNativeTest", "add", "(II)I", new Object[] {19, 23});
    check(Integer.valueOf(42).equals(nativeResult), "descriptor-aware native result");

    try {
      HostServices.call("shim.unsupported");
      throw new AssertionError("unsupported service unexpectedly succeeded");
    } catch (UnsupportedOperationException expected) {
      // The host's controlled error class is part of the v1 ABI.
    }
    System.out.println("host shim passed");
  }
}
