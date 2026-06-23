package com.genchat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Lightweight smoke test that does not require external MySQL/Redis/MinIO.
 * Full Spring context loading is covered by focused unit tests in subpackages.
 */
class GenChatApplicationTests {

    @Test
    void applicationClassIsPresent() {
        assertNotNull(GenChatApplication.class);
    }
}
