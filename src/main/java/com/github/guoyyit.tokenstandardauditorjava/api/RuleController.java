package com.github.guoyyit.tokenstandardauditorjava.api;

import com.github.guoyyit.tokenstandardauditorjava.api.dto.RuleDto;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.RuleSetDto;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleDescriptor;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleDescriptorParser;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleRegistry;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleSet;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for querying and updating rule sets.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRegistry ruleRegistry;
    private final RuleDescriptorParser descriptorParser;

    public RuleController(RuleRegistry ruleRegistry, RuleDescriptorParser descriptorParser) {
        this.ruleRegistry = ruleRegistry;
        this.descriptorParser = descriptorParser;
    }

    @GetMapping
    public ResponseEntity<List<RuleSetDto>> list(@RequestParam(required = false) String standard,
                                                 @RequestParam(required = false) String version) {
        if (standard != null && version != null) {
            RuleSet set = ruleRegistry.snapshot(standard, version);
            return ResponseEntity.ok(List.of(RuleSetDto.from(set)));
        }
        if (standard != null) {
            return ResponseEntity.ok(ruleRegistry.list().stream()
                    .filter(s -> s.standard().equals(standard))
                    .map(RuleSetDto::from)
                    .toList());
        }
        return ResponseEntity.ok(ruleRegistry.list().stream().map(RuleSetDto::from).toList());
    }

    @GetMapping("/standards")
    public ResponseEntity<List<String>> standards() {
        return ResponseEntity.ok(List.copyOf(ruleRegistry.standards()));
    }

    @PutMapping("/{standard}/{version}")
    public ResponseEntity<RuleSetDto> update(@PathVariable String standard,
                                             @PathVariable String version,
                                             @RequestBody RuleSetDto dto) {
        List<RuleDescriptor> descriptors = dto.rules().stream().map(RuleDto::toDescriptor).toList();
        RuleSet set = descriptorParser.parse(standard, version, dto.name(), descriptors);
        ruleRegistry.register(set);
        return ResponseEntity.ok(RuleSetDto.from(set));
    }
}
