package com.github.guoyyit.tokenstandardauditorjava.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.CheckRequestDto;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.RuleDto;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.RuleSetDto;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.AbiBuilder;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.TestFixtures;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("auditor.store-dir", () -> tempDir.resolve("checks").toString());
        registry.add("auditor.check.timeout-seconds", () -> "5");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String erc20Abi() {
        return AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
    }

    private String erc20Bytecode() {
        return TestFixtures.bytecodeOf(TestFixtures.erc20Functions(), TestFixtures.erc20Events());
    }

    @Test
    void submitAndGetCheck() throws Exception {
        CheckRequestDto request = new CheckRequestDto(erc20Abi(), erc20Bytecode(), "ERC20", null, null);
        MvcResult result = mockMvc.perform(post("/api/checks")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // poll until complete
        for (int i = 0; i < 50; i++) {
            MvcResult getResult = mockMvc.perform(get("/api/checks/" + id))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = objectMapper.readTree(getResult.getResponse().getContentAsString())
                    .get("status").asText();
            if ("COMPLETED".equals(status)) {
                assertTrue(objectMapper.readTree(getResult.getResponse().getContentAsString())
                        .get("report").get("compliant").asBoolean());
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Check did not complete");
    }

    @Test
    void getUnknownCheckReturns404() throws Exception {
        mockMvc.perform(get("/api/checks/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHECK_NOT_FOUND"));
    }

    @Test
    void cancelCompletedCheckIsNoop() throws Exception {
        CheckRequestDto request = new CheckRequestDto(erc20Abi(), erc20Bytecode(), "ERC20", null, null);
        MvcResult submit = mockMvc.perform(post("/api/checks")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();
        String id = objectMapper.readTree(submit.getResponse().getContentAsString()).get("id").asText();

        // wait for completion
        for (int i = 0; i < 50; i++) {
            String status = objectMapper.readTree(mockMvc.perform(get("/api/checks/" + id))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            if ("COMPLETED".equals(status)) {
                break;
            }
            Thread.sleep(100);
        }

        mockMvc.perform(post("/api/checks/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void parseAbiReturnsStructuredResult() throws Exception {
        mockMvc.perform(post("/api/parse/abi")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("abi", erc20Abi()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.functions").isArray())
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void parseBadAbiReturnsDistinguishableError() throws Exception {
        mockMvc.perform(post("/api/parse/abi")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("abi", "{bad"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ABI_PARSE_ERROR"));
    }

    @Test
    void parseBadBytecodeReturnsDistinguishableError() throws Exception {
        mockMvc.perform(post("/api/parse/bytecode")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("bytecode", "0xzz"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BYTECODE_PARSE_ERROR"));
    }

    @Test
    void listRuleSets() throws Exception {
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.standard=='ERC20')]").exists())
                .andExpect(jsonPath("$[?(@.standard=='ERC721')]").exists())
                .andExpect(jsonPath("$[?(@.standard=='ERC1155')]").exists());
    }

    @Test
    void updateRuleSetAtRuntime() throws Exception {
        RuleSetDto update = new RuleSetDto("ERC20", "1.0", "ERC-20 Updated",
                List.of(new RuleDto("CUSTOM-001", "WARNING", "custom rule", "REQUIRED_FUNCTION",
                        Map.of("signature", "transfer(address,uint256)"))));
        mockMvc.perform(put("/api/rules/ERC20/1.0")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ERC-20 Updated"))
                .andExpect(jsonPath("$.rules[0].id").value("CUSTOM-001"));

        // subsequent check uses the updated rule set
        CheckRequestDto request = new CheckRequestDto(erc20Abi(), erc20Bytecode(), "ERC20", null, null);
        MvcResult submit = mockMvc.perform(post("/api/checks")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();
        String id = objectMapper.readTree(submit.getResponse().getContentAsString()).get("id").asText();
        for (int i = 0; i < 50; i++) {
            String status = objectMapper.readTree(mockMvc.perform(get("/api/checks/" + id))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            if ("COMPLETED".equals(status)) {
                // the custom rule passes (transfer is present), so no ERROR findings
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Check did not complete");
    }

    @Test
    void updateRuleSetWithInvalidTypeReturnsError() throws Exception {
        RuleSetDto update = new RuleSetDto("ERC20", "1.0", "bad",
                List.of(new RuleDto("BAD-001", "ERROR", "bad", "BOGUS", Map.of())));
        mockMvc.perform(put("/api/rules/ERC20/1.0")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RULE_CONFIGURATION_ERROR"));
    }
}
