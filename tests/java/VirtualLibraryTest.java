/** Exercises the explicit Level 0 virtual-native-library opt-in. */
public final class VirtualLibraryTest {
  static {
    System.loadLibrary("awt");
  }

  private static native int afterVirtualLibrary(int value);

  public static void main(String[] args) {
    if (afterVirtualLibrary(41) != 42) {
      throw new AssertionError("virtual library did not reach host-native fallback");
    }
    System.out.println("virtual native library passed");
  }
}
