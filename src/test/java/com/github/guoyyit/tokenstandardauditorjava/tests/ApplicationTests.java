package com.github.guoyyit.tokenstandardauditorjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * @author Vincent
 * @version 1.0
 */
@SpringBootTest
public class ApplicationTests {

    private static final String TEST_DATA_DIR =
            System.getProperty("java.io.tmpdir") + "/auditor-apptest-" + UUID.randomUUID();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("auditor.data-dir", () -> TEST_DATA_DIR);
    }

    @Test
    public void contentLoad() {
    }
}
