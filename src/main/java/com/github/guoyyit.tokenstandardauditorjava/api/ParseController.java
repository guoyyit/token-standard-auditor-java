package com.github.guoyyit.tokenstandardauditorjava.api;

import com.github.guoyyit.tokenstandardauditorjava.api.dto.ErrorDto;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.BytecodeInfo;
import com.github.guoyyit.tokenstandardauditorjava.parse.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.BytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.ParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for parsing ABI and bytecode inputs, returning structured parse results or
 * distinguishable errors.
 */
@RestController
@RequestMapping("/api/parse")
public class ParseController {

    private final AbiParser abiParser;
    private final BytecodeParser bytecodeParser;

    public ParseController(AbiParser abiParser, BytecodeParser bytecodeParser) {
        this.abiParser = abiParser;
        this.bytecodeParser = bytecodeParser;
    }

    @PostMapping("/abi")
    public ResponseEntity<?> parseAbi(@RequestBody Map<String, String> body) {
        try {
            AbiParser.ParsedAbi parsed = abiParser.parse(body.get("abi"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("functions", parsed.functions().stream().map(this::toFunctionDto).toList());
            result.put("events", parsed.events().stream().map(this::toEventDto).toList());
            return ResponseEntity.ok(result);
        } catch (ParseException e) {
            return ResponseEntity.badRequest().body(new ErrorDto(e.getCode(), e.getMessage()));
        }
    }

    @PostMapping("/bytecode")
    public ResponseEntity<?> parseBytecode(@RequestBody Map<String, String> body) {
        try {
            BytecodeInfo info = bytecodeParser.parse(body.get("bytecode"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("size", info.size());
            result.put("metadataPresent", info.metadataPresent());
            result.put("selectors", info.selectors());
            result.put("topics", info.topics());
            return ResponseEntity.ok(result);
        } catch (ParseException e) {
            return ResponseEntity.badRequest().body(new ErrorDto(e.getCode(), e.getMessage()));
        }
    }

    private Map<String, Object> toFunctionDto(AbiFunction f) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", f.name());
        dto.put("signature", f.signature());
        dto.put("selector", f.selector());
        dto.put("stateMutability", f.stateMutability());
        dto.put("inputs", f.inputs().stream().map(this::toParamDto).toList());
        dto.put("outputs", f.outputs().stream().map(this::toParamDto).toList());
        return dto;
    }

    private Map<String, Object> toEventDto(AbiEvent e) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", e.name());
        dto.put("signature", e.signature());
        dto.put("topic", e.topic());
        dto.put("inputs", e.inputs().stream().map(p -> {
            Map<String, Object> param = toParamDto(p);
            param.put("indexed", p.indexed());
            return param;
        }).toList());
        return dto;
    }

    private Map<String, Object> toParamDto(com.github.guoyyit.tokenstandardauditorjava.model.AbiParam p) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", p.name());
        dto.put("type", p.type());
        dto.put("canonicalType", p.canonicalType());
        return dto;
    }
}
