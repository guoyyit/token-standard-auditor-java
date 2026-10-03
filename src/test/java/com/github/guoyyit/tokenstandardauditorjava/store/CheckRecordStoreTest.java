package com.github.guoyyit.tokenstandardauditorjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckReport;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckRecordStoreTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private CheckRecordStore newStore() throws Exception {
        CheckRecordStore store = new CheckRecordStore(tempDir.toString(), objectMapper);
        store.init();
        return store;
    }

    private CheckRecord completedRecord(String id) {
        CheckRecord record = new CheckRecord(id, CheckStatus.COMPLETED, "ERC20", "1.0",
                "abi-hash", "bytecode-hash", Instant.now());
        record.setCompletedAt(Instant.now());
        CheckReport report = new CheckReport(id, "ERC20", "1.0", "abi-hash", "bytecode-hash",
                List.of(new Finding("R1", Severity.ERROR, "ERC20", "1.0", "bad", null)),
                1, 0, 0, false, Instant.now());
        record.setReport(report);
        return record;
    }

    @Test
    void completedRecordSurvivesRestart() throws Exception {
        CheckRecordStore store = newStore();
        CheckRecord record = completedRecord("check-1");
        store.save(record);

        // simulate restart: new store instance, same directory
        CheckRecordStore restarted = newStore();
        Optional<CheckRecord> loaded = restarted.load("check-1");
        assertTrue(loaded.isPresent());
        assertEquals(CheckStatus.COMPLETED, loaded.get().getStatus());
        assertEquals("ERC20", loaded.get().getStandard());
        assertEquals(1, loaded.get().getReport().findings().size());
        assertEquals("bad", loaded.get().getReport().findings().get(0).message());
    }

    @Test
    void incompleteRecordsAreDiscardedOnRestart() throws Exception {
        CheckRecordStore store = newStore();
        CheckRecord pending = new CheckRecord("pending-1", CheckStatus.PENDING, "ERC20", "1.0",
                "h1", "h2", Instant.now());
        CheckRecord running = new CheckRecord("running-1", CheckStatus.RUNNING, "ERC20", "1.0",
                "h3", "h4", Instant.now());
        store.save(pending);
        store.save(running);

        CheckRecordStore restarted = newStore();
        assertFalse(restarted.load("pending-1").isPresent());
        assertFalse(restarted.load("running-1").isPresent());
    }

    @Test
    void failedRecordSurvivesRestart() throws Exception {
        CheckRecordStore store = newStore();
        CheckRecord failed = new CheckRecord("failed-1", CheckStatus.FAILED, "ERC20", "1.0",
                "h1", "h2", Instant.now());
        failed.setErrorCode("ABI_PARSE_ERROR");
        failed.setErrorMessage("bad abi");
        failed.setCompletedAt(Instant.now());
        store.save(failed);

        CheckRecordStore restarted = newStore();
        Optional<CheckRecord> loaded = restarted.load("failed-1");
        assertTrue(loaded.isPresent());
        assertEquals(CheckStatus.FAILED, loaded.get().getStatus());
        assertEquals("ABI_PARSE_ERROR", loaded.get().getErrorCode());
    }

    @Test
    void cancelledRecordSurvivesRestart() throws Exception {
        CheckRecordStore store = newStore();
        CheckRecord cancelled = new CheckRecord("cancelled-1", CheckStatus.CANCELLED, "ERC20", "1.0",
                "h1", "h2", Instant.now());
        cancelled.setCompletedAt(Instant.now());
        store.save(cancelled);

        CheckRecordStore restarted = newStore();
        Optional<CheckRecord> loaded = restarted.load("cancelled-1");
        assertTrue(loaded.isPresent());
        assertEquals(CheckStatus.CANCELLED, loaded.get().getStatus());
    }

    @Test
    void listReturnsAllRecords() throws Exception {
        CheckRecordStore store = newStore();
        store.save(completedRecord("a"));
        store.save(completedRecord("b"));
        assertEquals(2, store.list().size());
    }
}
