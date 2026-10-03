package com.github.guoyyit.tokenstandardauditorjava.api.dto;

import java.util.List;

/**
 * Input for a batch of compliance checks.
 *
 * @param checks list of check requests
 */
public record BatchCheckRequestDto(
        List<CheckRequestDto> checks
) {
}
