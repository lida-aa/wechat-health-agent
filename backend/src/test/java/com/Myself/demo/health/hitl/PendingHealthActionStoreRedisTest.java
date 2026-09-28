package com.Myself.demo.health.hitl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingHealthActionStoreRedisTest {

    @Test
    void restoresPendingActionFromRedisAfterRestart() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        Clock clock = Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.UTC);
        PendingHealthActionStore first = new PendingHealthActionStore(
                clock, Duration.ofMinutes(15), redis);
        first.createMedicationDraft("user-a", "二甲双胍", "1片", "08:00,20:00", 20);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("health:pending-action:user-a"), json.capture(), any(Duration.class));
        when(values.get("health:pending-action:user-a")).thenReturn(json.getValue());

        PendingHealthActionStore restarted = new PendingHealthActionStore(
                clock, Duration.ofMinutes(15), redis);

        assertThat(restarted.currentForUser("user-a")).get().satisfies(action -> {
            assertThat(action.type()).isEqualTo(PendingHealthAction.Type.ADD_MEDICATION);
            assertThat(action.status()).isEqualTo(PendingHealthAction.Status.WAITING_CONFIRMATION);
            MedicationDraft draft = action.payloadAs(MedicationDraft.class);
            assertThat(draft.name()).isEqualTo("二甲双胍");
            assertThat(draft.schedule()).isEqualTo("08:00,20:00");
            assertThat(draft.remaining()).isEqualTo(20);
        });
    }
}
