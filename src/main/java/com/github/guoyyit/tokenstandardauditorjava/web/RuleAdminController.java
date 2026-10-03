package com.github.guoyyit.tokenstandardauditorjava.web;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleDefinition;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleSetRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for managing versioned rule sets at runtime.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleAdminController {

    private final RuleSetRegistry registry;

    public RuleAdminController(RuleSetRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<RuleDefinition> listAll() {
        return registry.listAll();
    }

    @GetMapping("/{standard}/{version}")
    public ResponseEntity<?> getOne(@PathVariable String standard, @PathVariable String version) {
        TokenStandard tokenStandard = AuditController.parseStandard(standard);
        Optional<RuleDefinition> definition = registry.find(tokenStandard, version);
        if (definition.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "no rule set for " + tokenStandard + " " + version));
        }
        return ResponseEntity.ok(definition.get());
    }

    @PutMapping("/{standard}/{version}")
    public Map<String, Object> upsert(@PathVariable String standard, @PathVariable String version,
                                      @RequestBody RuleDefinition definition) {
        TokenStandard tokenStandard = AuditController.parseStandard(standard);
        if (definition.standard() != tokenStandard || !definition.version().equals(version)) {
            throw new AuditException(ErrorCode.UNKNOWN_STANDARD_VERSION,
                    "path standard/version do not match rule definition body");
        }
        registry.upsert(definition);
        return Map.of("updated", true, "standard", tokenStandard.name(), "version", version);
    }
}
