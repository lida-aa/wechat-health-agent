package com.Myself.demo.security;

import com.Myself.demo.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Order(-200)
@Component
public class HealthAgentStartupValidator implements CommandLineRunner {

    private final String llmApiKey;
    private final String llmModel;
    private final String llmBaseUrl;

    public HealthAgentStartupValidator(@Value("${llm.api-key:}") String llmApiKey,
                                       @Value("${llm.model:}") String llmModel,
                                       @Value("${llm.base-url:}") String llmBaseUrl) {
        this.llmApiKey = normalize(llmApiKey);
        this.llmModel = normalize(llmModel);
        this.llmBaseUrl = normalize(llmBaseUrl);
    }

    @Override
    public void run(String... args) {
        requireNonBlank("llm.api-key / DASHSCOPE_API_KEY", llmApiKey);
        requireNonBlank("llm.model / LLM_MODEL", llmModel);
        validateBaseUrl(llmBaseUrl);
        JwtUtil.validateConfiguration();
        log.info("健康 Agent 启动配置校验通过");
    }

    private static void validateBaseUrl(String value) {
        requireNonBlank("llm.base-url / LLM_BASE_URL", value);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("unsupported URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("llm.base-url must be a valid HTTP(S) URL", exception);
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + name);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
