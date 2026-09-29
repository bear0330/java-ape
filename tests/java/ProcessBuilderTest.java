import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProcessBuilderTest {
    private static final String INPUT = "hello";
    private static final String ENVIRONMENT_VALUE = "present";

    private ProcessBuilderTest() {
    }

    public static void main(String[] args) throws Exception {
        boolean windows = args.length == 1 && args[0].equals("--windows");
        if (args.length > 0 && !windows) {
            throw new IllegalArgumentException("usage: ProcessBuilderTest [--windows]");
        }

        Path directory = Files.createTempDirectory("java-ape-process-builder-");
        Path marker = directory.resolve(".process-builder-cwd");
        Files.writeString(marker, "marker", StandardCharsets.UTF_8);
        try {
            Process process = start(windows, directory);
            process.getOutputStream().write((INPUT + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            require(exitCode == 23, "unexpected exit code: " + exitCode);
            require(stdout.equals("out:hello|env:present|cwd:true"),
                    "unexpected stdout: " + stdout);
            require(stderr.equals("err:hello"), "unexpected stderr: " + stderr);
        } finally {
            Files.deleteIfExists(marker);
            Files.deleteIfExists(directory);
        }

        System.out.println("ProcessBuilder child process passed");
    }

    private static Process start(boolean windows, Path directory) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command(windows));
        builder.directory(directory.toFile());
        builder.environment().put("JAVA_APE_PROCESS_TEST_ENV", ENVIRONMENT_VALUE);
        return builder.start();
    }

    private static List<String> command(boolean windows) {
        if (windows) {
            return List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "$inputText=[Console]::In.ReadToEnd().Trim();"
                            + "$cwd=Test-Path .process-builder-cwd;"
                            + "[Console]::Out.Write('out:'+$inputText+'|env:'+$env:JAVA_APE_PROCESS_TEST_ENV+'|cwd:'+$cwd.ToString().ToLower());"
                            + "[Console]::Error.Write('err:'+$inputText);"
                            + "exit 23");
        }

        return List.of(
                "/bin/sh",
                "-c",
                "input=$(cat); "
                        + "printf 'out:%s|env:%s|cwd:%s' \"$input\" \"$JAVA_APE_PROCESS_TEST_ENV\" "
                        + "\"$(test -f .process-builder-cwd && printf true || printf false)\"; "
                        + "printf 'err:%s' \"$input\" >&2; exit 23");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
