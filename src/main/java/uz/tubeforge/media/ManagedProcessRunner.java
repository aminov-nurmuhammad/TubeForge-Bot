package uz.tubeforge.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class ManagedProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(ManagedProcessRunner.class);
    private static final int MAX_CAPTURE_CHARS = 8_000_000;

    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public ProcessResult run(String processId, List<String> command, Path workingDirectory,
                             Duration timeout, Consumer<String> lineConsumer) {
        Process process = null;
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "process-output-" + processId.substring(0, Math.min(8, processId.length())));
            thread.setDaemon(true);
            return thread;
        });
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true);
            builder.environment().put("PYTHONUTF8", "1");
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("NO_COLOR", "1");
            process = builder.start();
            activeProcesses.put(processId, process);
            StringBuilder output = new StringBuilder();
            Process runningProcess = process;
            Future<?> reader = readerExecutor.submit(() -> readOutput(runningProcess, output, lineConsumer));

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                awaitReader(reader);
                return new ProcessResult(-1, output.toString(), true, false);
            }
            awaitReader(reader);
            return new ProcessResult(process.exitValue(), output.toString(), false, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) terminate(process);
            return new ProcessResult(-1, "Interrupted", false, true);
        } catch (IOException e) {
            String executable = command == null || command.isEmpty() ? "media tool" : command.get(0);
            log.error("Could not start media executable '{}': {}", executable, e.getMessage());
            throw new MediaProcessingException("PROCESS_START_FAILED",
                    "Could not start the configured media tool. Check this executable path: " + executable, e);
        } finally {
            activeProcesses.remove(processId);
            readerExecutor.shutdownNow();
        }
    }

    public ProcessResult capture(List<String> command, Path workingDirectory, Duration timeout) {
        return run("capture-" + System.nanoTime(), command, workingDirectory, timeout, ignored -> {});
    }

    public boolean cancel(String processId) {
        Process process = activeProcesses.get(processId);
        if (process == null) return false;
        terminate(process);
        return true;
    }

    private void readOutput(Process process, StringBuilder output, Consumer<String> lineConsumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < MAX_CAPTURE_CHARS) {
                    int remaining = MAX_CAPTURE_CHARS - output.length();
                    output.append(line, 0, Math.min(line.length(), remaining)).append('\n');
                }
                lineConsumer.accept(line);
            }
        } catch (IOException e) {
            log.debug("Process output stream closed: {}", e.getMessage());
        }
    }

    private void awaitReader(Future<?> reader) {
        try {
            reader.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            reader.cancel(true);
        }
    }

    private void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
