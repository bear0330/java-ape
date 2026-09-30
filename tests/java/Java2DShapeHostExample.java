import java.awt.geom.PathIterator;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Exercises two independent Java2D native ShapeSpanIterator paths through a Python host. */
public final class Java2DShapeHostExample {
  private static final Class<?> ITERATOR_TYPE = iteratorType();

  public static void main(String[] args) throws ReflectiveOperationException {
    Object first = newIterator(false);
    Object second = newIterator(true);

    configure(first, 0, 0, 100, 100);
    configure(second, -100, -100, 100, 100);

    invoke(first, "moveTo", new Class<?>[] {float.class, float.class}, 10f, 20f);
    invoke(second, "moveTo", new Class<?>[] {float.class, float.class}, -5f, 60f);
    invoke(first, "lineTo", new Class<?>[] {float.class, float.class}, 30f, 40f);
    invoke(second, "lineTo", new Class<?>[] {float.class, float.class}, 70f, 80f);
    invoke(first, "pathDone", new Class<?>[0]);
    invoke(second, "pathDone", new Class<?>[0]);

    assertBounds(first, 10, 20, 30, 40);
    assertBounds(second, -5, 60, 71, 81);

    invoke(first, "dispose", new Class<?>[0]);
    invoke(second, "dispose", new Class<?>[0]);
    System.out.println("Java2D ShapeSpanIterator host replacement passed");
  }

  private static void assertBounds(Object iterator, int... expected)
      throws ReflectiveOperationException {
    int[] bounds = new int[4];
    invoke(iterator, "getPathBox", new Class<?>[] {int[].class}, (Object) bounds);

    if (!Arrays.equals(bounds, expected)) {
      throw new AssertionError("expected " + Arrays.toString(expected) + ", got "
          + Arrays.toString(bounds));
    }
  }

  private static void configure(Object iterator, int left, int top, int right, int bottom)
      throws ReflectiveOperationException {
    invoke(
        iterator,
        "setOutputAreaXYXY",
        new Class<?>[] {int.class, int.class, int.class, int.class},
        left,
        top,
        right,
        bottom);
    invoke(iterator, "setRule", new Class<?>[] {int.class}, PathIterator.WIND_NON_ZERO);
  }

  private static void invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments)
      throws ReflectiveOperationException {
    Method method = ITERATOR_TYPE.getMethod(name, parameterTypes);
    method.invoke(target, arguments);
  }

  private static Object newIterator(boolean adjust) throws ReflectiveOperationException {
    return ITERATOR_TYPE.getConstructor(boolean.class).newInstance(adjust);
  }

  private static Class<?> iteratorType() {
    try {
      return Class.forName("sun.java2d.pipe.ShapeSpanIterator");
    } catch (ClassNotFoundException error) {
      throw new AssertionError(error);
    }
  }
}
