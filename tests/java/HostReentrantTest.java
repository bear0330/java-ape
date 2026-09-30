/** Exercises a Host callback that causes another unresolved native invocation. */
public final class HostReentrantTest {
  private static native int outer();
  private static native int inner(int value);

  public static void main(String[] arguments) {
    if (outer() != 42) {
      throw new AssertionError("nested Host-native result was not returned");
    }
    System.out.println("host-native reentrancy passed");
  }

  private static int invokeInner() {
    return inner(41);
  }
}
