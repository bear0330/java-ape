/** Verifies that Java APE carries OpenJDK's static libjavajpeg codec. */
public final class JpegStaticLibraryTest {
  private JpegStaticLibraryTest() {
  }

  public static void main(String[] args) {
    System.loadLibrary("javajpeg");
    System.out.println("static libjavajpeg loaded");
  }
}
