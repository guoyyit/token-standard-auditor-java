package com.github.guoyyit.tokenstandardauditorjava.api;

import com.github.guoyyit.tokenstandardauditorjava.api.dto.BatchCheckRequestDto;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.CheckRequestDto;
import com.github.guoyyit.tokenstandardauditorjava.api.dto.CheckResponseDto;
import com.github.guoyyit.tokenstandardauditorjava.check.CheckService;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for submitting and querying compliance checks.
 */
@RestController
@RequestMapping("/api/checks")
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping
    public ResponseEntity<CheckResponseDto> submit(@RequestBody CheckRequestDto dto) {
        CheckRecord record = checkService.submit(toRequest(dto));
        return ResponseEntity.accepted().body(CheckResponseDto.from(record));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<CheckResponseDto>> submitBatch(@RequestBody BatchCheckRequestDto dto) {
        List<CheckRequest> requests = dto.checks().stream().map(this::toRequest).toList();
        List<CheckRecord> records = checkService.submitBatch(requests);
        return ResponseEntity.accepted().body(records.stream().map(CheckResponseDto::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckResponseDto> get(@PathVariable String id) {
        CheckRecord record = checkService.get(id);
        return ResponseEntity.ok(CheckResponseDto.from(record));
    }

    @GetMapping
    public ResponseEntity<List<CheckResponseDto>> list() {
        return ResponseEntity.ok(checkService.list().stream().map(CheckResponseDto::from).toList());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CheckResponseDto> cancel(@PathVariable String id) {
        CheckRecord record = checkService.cancel(id);
        return ResponseEntity.ok(CheckResponseDto.from(record));
    }

    private CheckRequest toRequest(CheckRequestDto dto) {
        return new CheckRequest(dto.abi(), dto.bytecode(), dto.standard(), dto.version(), dto.timeoutSeconds());
    }
}
