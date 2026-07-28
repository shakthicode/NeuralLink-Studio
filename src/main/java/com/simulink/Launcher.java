package com.simulink;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Native-packaging entry point.
 *
 * Keeping the JavaFX Application subclass out of the native launcher's main
 * class avoids the Java launcher's special JavaFX module detection when
 * JavaFX is supplied as application libraries by Maven/jpackage.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> writeStartupError(thread, error));
        try {
            App.main(args);
        } catch (Throwable error) {
            writeStartupError(Thread.currentThread(), error);
            throw error;
        }
    }

    private static void writeStartupError(Thread thread, Throwable error) {
        try {
            Path logDirectory = Path.of(
                    System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")),
                    "NeuralLink Studio",
                    "logs");
            Files.createDirectories(logDirectory);

            StringWriter stackTrace = new StringWriter();
            error.printStackTrace(new PrintWriter(stackTrace));
            String report = System.lineSeparator()
                    + "============================================================" + System.lineSeparator()
                    + "NeuralLink Studio startup failure" + System.lineSeparator()
                    + "Time: " + LocalDateTime.now() + System.lineSeparator()
                    + "Thread: " + thread.getName() + System.lineSeparator()
                    + "Java: " + System.getProperty("java.version") + System.lineSeparator()
                    + "App path: " + System.getProperty("jpackage.app-path", "not set") + System.lineSeparator()
                    + stackTrace + System.lineSeparator();

            Files.writeString(
                    logDirectory.resolve("startup-error.log"),
                    report,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            error.printStackTrace();
        }
    }
}
