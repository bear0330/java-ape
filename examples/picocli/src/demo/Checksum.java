package demo;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "checksum", mixinStandardHelpOptions = true,
    description = "Print a digest for a file.")
public final class Checksum implements Callable<Integer> {
  @Option(names = {"-a", "--algorithm"}, defaultValue = "SHA-256")
  String algorithm;

  @Parameters(index = "0", paramLabel = "FILE")
  String file;

  @Override public Integer call() throws Exception {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    try (FileInputStream input = new FileInputStream(file)) {
      byte[] block = new byte[8192];
      for (int count; (count = input.read(block)) != -1;) digest.update(block, 0, count);
    }
    StringBuilder hex = new StringBuilder();
    for (byte value : digest.digest()) hex.append(String.format("%02x", value));
    System.out.println(hex + "  " + file);
    return 0;
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Checksum()).execute(args));
  }
}
