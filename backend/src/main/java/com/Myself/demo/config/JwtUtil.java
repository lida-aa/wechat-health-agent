package com.Myself.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final int MIN_SECRET_LENGTH = 32;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static volatile byte[] secret;

    public static String createToken(String username, String role) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            String payload = encoder.encodeToString(mapper.writeValueAsBytes(Map.of(
                    "sub", username,
                    "role", role,
                    "exp", System.currentTimeMillis() / 1000 + 86400)));
            String data = header + "." + payload;
            String sig = hmacSha256(data);
            return data + "." + sig;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String sig = hmacSha256(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) return null;
            Map<String, Object> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Object expiration = claims.get("exp");
            if (!(expiration instanceof Number)
                    || ((Number) expiration).longValue() <= System.currentTimeMillis() / 1000) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret(), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    public static void validateConfiguration() {
        String configured = configuredSecret();
        if (configured == null || configured.isBlank()) {
            log.warn("未配置 HEALTH_AGENT_JWT_SECRET，将使用进程内随机密钥；应用重启后旧令牌会失效");
            return;
        }
        validateLength(configured);
    }

    private static byte[] secret() {
        byte[] current = secret;
        if (current != null) {
            return current;
        }
        synchronized (JwtUtil.class) {
            if (secret == null) {
                secret = loadSecret();
            }
            return secret;
        }
    }

    private static byte[] loadSecret() {
        String configured = configuredSecret();
        if (configured != null && !configured.isBlank()) {
            validateLength(configured);
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }

    private static String configuredSecret() {
        String configured = System.getProperty("health.agent.jwt-secret");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("HEALTH_AGENT_JWT_SECRET");
        }
        return configured;
    }

    private static void validateLength(String configured) {
        if (configured.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "HEALTH_AGENT_JWT_SECRET must contain at least " + MIN_SECRET_LENGTH + " characters");
        }
    }
}
