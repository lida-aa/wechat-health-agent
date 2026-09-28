package com.Myself.demo.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthAgentStartupValidatorTest {

    @AfterEach
    void clearJwtSecretProperty() {
        System.clearProperty("health.agent.jwt-secret");
    }

    @Test
    void acceptsCompleteConfiguration() {
        System.setProperty("health.agent.jwt-secret", "a-secure-jwt-secret-with-32-characters");
        HealthAgentStartupValidator validator = new HealthAgentStartupValidator(
                "test-api-key", "qwen-plus", "https://example.test/v1");

        assertThatCode(validator::run).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingModelApiKey() {
        HealthAgentStartupValidator validator = new HealthAgentStartupValidator(
                "", "qwen-plus", "https://example.test/v1");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHSCOPE_API_KEY");
    }

    @Test
    void rejectsInvalidModelBaseUrl() {
        HealthAgentStartupValidator validator = new HealthAgentStartupValidator(
                "test-api-key", "qwen-plus", "file:///tmp/model");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP(S)");
    }

    @Test
    void rejectsShortConfiguredJwtSecret() {
        System.setProperty("health.agent.jwt-secret", "too-short");
        HealthAgentStartupValidator validator = new HealthAgentStartupValidator(
                "test-api-key", "qwen-plus", "https://example.test/v1");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }
}
