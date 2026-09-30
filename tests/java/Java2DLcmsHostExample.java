import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;

/**
 * Converts one sRGB pixel to grayscale through the OpenJDK LCMS native API.
 *
 * <p>The test supplies no liblcms JNI library. The Python Host Services
 * provider implements the profile and pixel-transform calls instead.</p>
 */
public final class Java2DLcmsHostExample {
  public static void main(String[] args) {
    BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
    source.setRGB(0, 0, new Color(0x80, 0x40, 0x20).getRGB());

    BufferedImage destination = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
    ColorConvertOp convert = new ColorConvertOp(
        ColorSpace.getInstance(ColorSpace.CS_sRGB),
        ColorSpace.getInstance(ColorSpace.CS_GRAY),
        null);
    convert.filter(source, destination);

    int red = (destination.getRGB(0, 0) >>> 16) & 0xff;
    int green = (destination.getRGB(0, 0) >>> 8) & 0xff;
    int blue = destination.getRGB(0, 0) & 0xff;
    if (red != green || green != blue) {
      throw new AssertionError("destination is not grayscale: "
          + Integer.toHexString(destination.getRGB(0, 0)));
    }
    if (red == 0 || red == 0x80) {
      throw new AssertionError("color conversion did not change the pixel: " + red);
    }
    System.out.printf("Java2D LCMS host conversion passed: gray=%d%n", red);
  }
}
