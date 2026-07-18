package uz.tubeforge.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.tubeforge.config.MediaProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final Set<String> TEMP_SUFFIXES = Set.of(".part", ".ytdl", ".temp", ".tmp");

    private final MediaProperties properties;
    private final Clock clock;

    public StorageService(MediaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        ensureDirectory(properties.storagePath());
    }

    public Path jobDirectory(String jobId) {
        if (!jobId.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("Invalid job ID");
        Path directory = properties.storagePath().toAbsolutePath().normalize().resolve(jobId).normalize();
        if (!directory.startsWith(properties.storagePath().toAbsolutePath().normalize())) {
            throw new SecurityException("Invalid storage path");
        }
        ensureDirectory(directory);
        return directory;
    }

    public List<Path> resultFiles(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> TEMP_SUFFIXES.stream().noneMatch(suffix -> path.getFileName().toString().endsWith(suffix)))
                    .filter(path -> !path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(this::safeSize).reversed())
                    .toList();
        } catch (IOException e) {
            throw new MediaProcessingException("STORAGE_READ_FAILED", "The processed file could not be located.", e);
        }
    }

    public Path requireFirst(Path directory, Predicate<Path> filter) {
        return resultFiles(directory).stream().filter(filter).findFirst()
                .orElseThrow(() -> new MediaProcessingException("OUTPUT_MISSING", "Processing finished but no output file was created."));
    }

    public Path zip(Path directory, List<Path> files, String name) {
        Path zip = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(zip, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             ZipOutputStream archive = new ZipOutputStream(output)) {
            for (Path file : files) {
                archive.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, archive);
                archive.closeEntry();
            }
            return zip;
        } catch (IOException e) {
            throw new MediaProcessingException("ZIP_FAILED", "The files could not be packaged.", e);
        }
    }

    public Path writeText(Path directory, String name, String content) {
        Path destination = directory.resolve(name);
        try {
            return Files.writeString(destination, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MediaProcessingException("TEXT_EXPORT_FAILED", "The transcript could not be created.", e);
        }
    }

    public void deleteJobDirectory(String jobId) {
        Path directory = properties.storagePath().toAbsolutePath().normalize().resolve(jobId).normalize();
        if (!directory.startsWith(properties.storagePath().toAbsolutePath().normalize()) || !Files.exists(directory)) return;
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not delete job storage {}: {}", jobId, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void removeExpiredStorage() {
        Instant cutoff = clock.instant().minus(properties.cacheRetention());
        Path root = properties.storagePath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(directory -> {
                try {
                    Instant modified = Files.getLastModifiedTime(directory).toInstant();
                    if (modified.isBefore(cutoff)) deleteJobDirectory(directory.getFileName().toString());
                } catch (IOException e) {
                    log.debug("Could not inspect storage directory {}", directory, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean media storage: {}", e.getMessage());
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create media storage directory: " + directory, e);
        }
    }

    private long safeSize(Path path) {
        try { return Files.size(path); } catch (IOException ignored) { return 0; }
    }
}
