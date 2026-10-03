package com.github.guoyyit.tokenstandardauditorjava.web;

import com.github.guoyyit.tokenstandardauditorjava.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditControllerTest {

    private static final String TEST_DATA_DIR =
            System.getProperty("java.io.tmpdir") + "/auditor-webtest-" + UUID.randomUUID();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("auditor.data-dir", () -> TEST_DATA_DIR);
    }

    @Autowired
    private TestRestTemplate rest;

    private Map<String, Object> submissionBody(String abi, String bytecode, String standard,
                                               String version) {
        return Map.of("contractId", "web-1", "abi", abi, "bytecode", bytecode,
                "standard", standard, "standardVersion", version == null ? "" : version);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitReport(String checkId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> response = rest.getForEntity("/api/audits/" + checkId, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("report not ready in time");
    }

    @Test
    void submitAndFetchReport() throws Exception {
        ResponseEntity<Map> submit = rest.postForEntity("/api/audits",
                submissionBody(TestFixtures.ERC20_ABI, TestFixtures.erc20Bytecode(), "erc20", null),
                Map.class);
        assertEquals(HttpStatus.OK, submit.getStatusCode());
        String checkId = (String) submit.getBody().get("checkId");
        assertNotNull(checkId);

        Map<String, Object> report = awaitReport(checkId);
        assertEquals("COMPLETED", report.get("status"));
        assertEquals("ERC20", report.get("standard"));
        assertEquals("v1", report.get("standardVersion"));
        assertNotNull(report.get("findings"));
    }

    @Test
    void batchSubmitReturnsIdsInOrder() {
        Map<String, Object> body = Map.of("submissions", List.of(
                submissionBody(TestFixtures.ERC20_ABI, TestFixtures.erc20Bytecode(), "ERC20", "v1"),
                submissionBody(TestFixtures.ERC721_ABI, TestFixtures.erc721Bytecode(), "ERC721", "v1")));
        ResponseEntity<Map> response = rest.postForEntity("/api/audits/batch", body, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> ids = (List<String>) response.getBody().get("checkIds");
        assertEquals(2, ids.size());
    }

    @Test
    void invalidAbiYieldsDistinguishableErrorCode() throws Exception {
        ResponseEntity<Map> submit = rest.postForEntity("/api/audits",
                submissionBody("[{\"type\":\"mystery\"}]", "0x00", "ERC20", null), Map.class);
        String checkId = (String) submit.getBody().get("checkId");
        Map<String, Object> report = awaitReport(checkId);
        assertEquals("FAILED", report.get("status"));
        assertTrue(((String) report.get("errorMessage")).contains("mystery"));
    }

    @Test
    void unknownStandardIsRejected() {
        ResponseEntity<Map> response = rest.postForEntity("/api/audits",
                submissionBody("[]", "0x00", "ERC999", null), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNKNOWN_STANDARD_VERSION", response.getBody().get("code"));
    }

    @Test
    void unknownCheckIdIs404() {
        ResponseEntity<Map> response = rest.getForEntity("/api/audits/no-such-id", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void cancelUnknownCheckReturnsFalse() {
        ResponseEntity<Map> response = rest.postForEntity("/api/audits/no-such-id/cancel", null,
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().get("cancelled"));
    }

    @Test
    void ruleAdminListsAndUpdatesRules() throws Exception {
        ResponseEntity<List> list = rest.getForEntity("/api/rules", List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertTrue(list.getBody().size() >= 4);

        ResponseEntity<Map> one = rest.getForEntity("/api/rules/ERC20/v1", Map.class);
        assertEquals(HttpStatus.OK, one.getStatusCode());
        Map<String, Object> definition = one.getBody();

        // update: make name() required with ERROR severity
        List<Map<String, Object>> functions = (List<Map<String, Object>>) definition.get("functions");
        for (Map<String, Object> function : functions) {
            if ("name".equals(function.get("name"))) {
                function.put("required", true);
                function.put("severity", "ERROR");
            }
        }
        ResponseEntity<Map> updated = rest.exchange("/api/rules/ERC20/v1", HttpMethod.PUT,
                new HttpEntity<>(definition), Map.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());

        // a contract without name() must now be reported with ERROR severity
        String abiNoName = TestFixtures.ERC20_ABI.replace(
                "{\"type\":\"function\",\"name\":\"name\",\"stateMutability\":\"view\",\"inputs\":[],\"outputs\":[{\"name\":\"\",\"type\":\"string\"}]},",
                "");
        ResponseEntity<Map> submit = rest.postForEntity("/api/audits",
                submissionBody(abiNoName, TestFixtures.erc20Bytecode(), "ERC20", "v1"), Map.class);
        Map<String, Object> report = awaitReport((String) submit.getBody().get("checkId"));
        List<Map<String, Object>> findings = (List<Map<String, Object>>) report.get("findings");
        assertTrue(findings.stream().anyMatch(f -> "function:name()".equals(f.get("ruleId"))
                && "ERROR".equals(f.get("severity"))), "findings: " + findings);
    }
}
