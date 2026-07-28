package com.simulink.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simulink.model.Block;
import com.simulink.model.ClockBlock;
import com.simulink.model.ConstantBlock;
import com.simulink.model.CosineBlock;
import com.simulink.model.DisplayBlock;
import com.simulink.model.GainBlock;
import com.simulink.model.IntegratorBlock;
import com.simulink.model.ScopeBlock;
import com.simulink.model.SineBlock;
import com.simulink.model.SumBlock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent local-process bridge to the MinGW C++ simulation engine.
 *
 * One JSON object is written per line and exactly one JSON response is read for
 * that request. Keeping the process alive avoids starting a new executable for
 * every 60 FPS simulation frame.
 */
public final class CppSimulationBackend implements Closeable {

    private final Gson gson = new Gson();
    private final AtomicLong requestIds = new AtomicLong();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final Map<Block, Double> previousInputs = new IdentityHashMap<>();
    private final Map<Block, Long> integratorVersions = new IdentityHashMap<>();

    public synchronized void start() throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }

        Path executable = resolveExecutable();
        if (!Files.isRegularFile(executable)) {
            throw new IOException(
                    "C++ backend executable not found: " + executable.toAbsolutePath()
                    + System.lineSeparator()
                    + "Build it with backend\\build-backend.bat (Windows) or "
                    + "backend/build-backend.sh (Linux/macOS).");
        }

        ProcessBuilder builder = new ProcessBuilder(executable.toAbsolutePath().toString());
        builder.directory(Paths.get("").toAbsolutePath().normalize().toFile());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = builder.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        JsonObject ping = request("ping", "System", 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);
        if (!ping.has("ok") || !ping.get("ok").getAsBoolean()) {
            close();
            throw new IOException("C++ backend handshake failed.");
        }
    }

    public synchronized double compute(Block block, List<Double> inputs, double clockTime) throws IOException {
        start();

        double input = inputs == null || inputs.isEmpty() ? 0.0 : inputs.get(0);
        double amplitude = 1.0;
        double frequency = 1.0;
        double phase = 0.0;
        double bias = 0.0;
        double parameter = 0.0;
        double dt = 0.0;
        double previousInput = input;
        boolean hasPreviousInput = false;
        String signs = "";
        String solver = "EULER";
        String blockType;

        if (block instanceof ClockBlock) {
            blockType = "Clock";
            input = clockTime;
        } else if (block instanceof SineBlock sine) {
            blockType = "Sine";
            amplitude = sine.getAmplitude();
            frequency = sine.getFrequency();
            phase = sine.getPhase();
            bias = sine.getBias();
        } else if (block instanceof CosineBlock cosine) {
            blockType = "Cosine";
            amplitude = cosine.getAmplitude();
            frequency = cosine.getFrequency();
            phase = cosine.getPhase();
            bias = cosine.getBias();
        } else if (block instanceof ScopeBlock) {
            blockType = "Scope";
        } else if (block instanceof ConstantBlock constant) {
            blockType = "Constant";
            parameter = constant.getConstantValue();
        } else if (block instanceof GainBlock gain) {
            blockType = "Gain";
            parameter = gain.getGainValue();
        } else if (block instanceof SumBlock sum) {
            blockType = "Sum";
            signs = sum.getSigns();
        } else if (block instanceof IntegratorBlock integrator) {
            blockType = "Integrator";
            dt = integrator.getTimeStep();
            solver = integrator.getSolverType().name();
            Long knownVersion = integratorVersions.get(block);
            if (knownVersion != null && knownVersion == integrator.getStateVersion()) {
                Double prior = previousInputs.get(block);
                if (prior != null) {
                    previousInput = prior;
                    hasPreviousInput = true;
                }
            } else {
                integratorVersions.put(block, integrator.getStateVersion());
                previousInputs.remove(block);
            }
        } else if (block instanceof DisplayBlock) {
            blockType = "Display";
        } else {
            throw new IOException("Unsupported block: " + block.getName());
        }

        JsonObject extra = new JsonObject();
        extra.addProperty("parameter", parameter);
        extra.addProperty("dt", dt);
        extra.addProperty("currentOutput", block.getLastOutput());
        extra.addProperty("previousInput", previousInput);
        extra.addProperty("hasPreviousInput", hasPreviousInput);
        extra.addProperty("signs", signs);
        extra.addProperty("solver", solver);
        extra.addProperty("inputs", joinInputs(inputs));
        JsonObject response = request("compute", blockType, input, clockTime,
                amplitude, frequency, phase, bias, extra);
        if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
            String message = response.has("error") ? response.get("error").getAsString() : "unknown backend error";
            throw new IOException("C++ backend rejected " + blockType + ": " + message);
        }
        double value = response.get("value").getAsDouble();
        if (block instanceof IntegratorBlock) {
            previousInputs.put(block, input);
        }
        return value;
    }

    private JsonObject request(String command, String block, double input, double time,
            double amplitude, double frequency, double phase, double bias) throws IOException {
        return request(command, block, input, time, amplitude, frequency, phase, bias, null);
    }

    private JsonObject request(String command, String block, double input, double time,
            double amplitude, double frequency, double phase, double bias,
            JsonObject extra) throws IOException {
        long id = requestIds.incrementAndGet();
        JsonObject request = new JsonObject();
        request.addProperty("id", id);
        request.addProperty("command", command);
        request.addProperty("block", block);
        request.addProperty("input", input);
        request.addProperty("time", time);
        request.addProperty("amplitude", amplitude);
        request.addProperty("frequency", frequency);
        request.addProperty("phase", phase);
        request.addProperty("bias", bias);
        if (extra != null) {
            for (Map.Entry<String, com.google.gson.JsonElement> entry : extra.entrySet()) {
                request.add(entry.getKey(), entry.getValue());
            }
        }

        writer.write(gson.toJson(request));
        writer.newLine();
        writer.flush();

        String line = reader.readLine();
        if (line == null) {
            throw new IOException("C++ backend terminated unexpectedly.");
        }
        JsonObject response = JsonParser.parseString(line).getAsJsonObject();
        if (!response.has("id") || response.get("id").getAsLong() != id) {
            throw new IOException("C++ backend response ID mismatch.");
        }
        return response;
    }

    private String joinInputs(List<Double> inputs) {
        if (inputs == null || inputs.isEmpty()) return "";
        StringBuilder values = new StringBuilder();
        for (double value : inputs) {
            if (values.length() > 0) values.append(',');
            values.append(Double.toString(value));
        }
        return values.toString();
    }

    private Path resolveExecutable() {
        String override = System.getProperty("neural.backend.path");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }

        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        String executableName = windows ? "simulation_backend.exe" : "simulation_backend";
        List<Path> candidates = new ArrayList<>();

        // Development layout: <project>/backend/simulation_backend(.exe)
        candidates.add(Paths.get("backend", executableName));

        // jpackage sets this property to the native launcher path. Installer assets
        // are copied beneath <app-image>/app/backend.
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path launcherDir = Paths.get(appPath).toAbsolutePath().normalize().getParent();
            if (launcherDir != null) {
                candidates.add(launcherDir.resolve("app").resolve("backend").resolve(executableName));
                candidates.add(launcherDir.resolve("backend").resolve(executableName));
            }
        }

        // Optional explicit installation directory supplied by build/launcher scripts.
        String appDir = System.getProperty("neural.app.dir");
        if (appDir != null && !appDir.isBlank()) {
            Path base = Paths.get(appDir).toAbsolutePath().normalize();
            candidates.add(base.resolve("app").resolve("backend").resolve(executableName));
            candidates.add(base.resolve("backend").resolve(executableName));
        }

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return candidates.get(0).toAbsolutePath().normalize();
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Process teardown continues.
            }
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Process teardown continues.
            }
        }
        if (process != null) {
            process.destroy();
        }
        writer = null;
        reader = null;
        process = null;
        previousInputs.clear();
        integratorVersions.clear();
    }
}
