/** Prints argv joined by | so zip .args merging can be asserted. */
public class EchoArgs {
  public static void main(String[] args) {
    System.out.println(String.join("|", args));
  }
}
