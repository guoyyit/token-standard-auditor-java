package com.github.guoyyit.tokenstandardauditorjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * File-based persistent store for {@link CheckRecord}s.
 *
 * <p>Records are written as JSON files using atomic moves so that a crash never leaves a partially
 * written report. On startup, non-terminal records (left over from a crash) are discarded: they
 * represent incomplete checks that must not be visible as reports.
 */
@Component
public class CheckRecordStore {

    private static final Logger log = LoggerFactory.getLogger(CheckRecordStore.class);

    private final Path directory;
    private final ObjectMapper objectMapper;
    private final Map<String, CheckRecord> cache = new ConcurrentHashMap<>();

    public CheckRecordStore(@Value("${auditor.store-dir:./data/checks}") String storeDir,
                            ObjectMapper objectMapper) {
        this.directory = Paths.get(storeDir);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(directory);
        loadAll();
    }

    /**
     * Persists the given record.
     */
    public void save(CheckRecord record) {
        cache.put(record.getId(), record);
        Path file = fileFor(record.getId());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            objectMapper.writeValue(tmp.toFile(), record);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailed) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StoreException("Failed to persist check record " + record.getId(), e);
        }
    }

    /**
     * Loads the record with the given id.
     */
    public Optional<CheckRecord> load(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    /**
     * Returns all records, ordered by submission time.
     */
    public List<CheckRecord> list() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Deletes the record with the given id.
     */
    public void delete(String id) {
        cache.remove(id);
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            throw new StoreException("Failed to delete check record " + id, e);
        }
    }

    private void loadAll() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> recordFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : recordFiles) {
                try {
                    CheckRecord record = objectMapper.readValue(file.toFile(), CheckRecord.class);
                    if (record.getStatus() == CheckStatus.PENDING || record.getStatus() == CheckStatus.RUNNING) {
                        log.warn("Discarding incomplete check record {} (status {}) after restart",
                                record.getId(), record.getStatus());
                        Files.deleteIfExists(file);
                        continue;
                    }
                    cache.put(record.getId(), record);
                } catch (IOException e) {
                    log.error("Failed to read check record file {}, ignoring", file, e);
                }
            }
        }
    }

    private Path fileFor(String id) {
        return directory.resolve(id + ".json");
    }

    /**
     * Thrown when a record cannot be persisted.
     */
    public static class StoreException extends RuntimeException {
        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
