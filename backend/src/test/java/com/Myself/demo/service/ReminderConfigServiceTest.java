package com.Myself.demo.service;

import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.entity.ReminderStatus;
import com.Myself.demo.support.InMemoryHealthReminderRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderConfigServiceTest {

    @Test
    void reminderIsStoredListedAndSoftCancelled() {
        ReminderConfigService service = service();

        HealthReminder reminder = service.add("user-a", null, "喝水", 8, 30);

        assertThat(reminder.getId()).isPositive();
        assertThat(service.list("user-a")).singleElement()
                .extracting(HealthReminder::getParams)
                .isEqualTo("喝水");
        assertThat(service.cancel("user-b", reminder.getId())).isNull();
        assertThat(service.cancel("user-a", reminder.getId())).isNotNull();
        assertThat(service.list("user-a")).isEmpty();
    }

    @Test
    void successfulReminderRunsOnlyOncePerDay() {
        ReminderConfigService service = service();
        service.add("user-a", null, "喝水", 8, 30);
        LocalDateTime firstRun = LocalDateTime.of(2026, 9, 27, 8, 30);

        HealthReminder claimed = service.claimDueReminders(firstRun).getFirst();
        assertThat(service.claimDueReminders(firstRun)).isEmpty();
        service.markSucceeded(claimed, firstRun.plusSeconds(2));

        assertThat(service.claimDueReminders(firstRun.plusMinutes(1))).isEmpty();
        assertThat(service.claimDueReminders(firstRun.plusDays(1))).hasSize(1);
    }

    @Test
    void failureUsesBackoffAndStopsAfterThreeAttempts() {
        ReminderConfigService service = service();
        HealthReminder reminder = service.add("user-a", null, "喝水", 8, 30);
        LocalDateTime firstRun = LocalDateTime.of(2026, 9, 27, 8, 30);

        HealthReminder first = service.claimDueReminders(firstRun).getFirst();
        service.markFailed(first, new IllegalStateException("private message"), firstRun);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(reminder.getAttemptCount()).isEqualTo(1);
        assertThat(reminder.getNextRetryAt()).isEqualTo(firstRun.plusMinutes(5));
        assertThat(reminder.getLastError()).isEqualTo("IllegalStateException");
        assertThat(service.claimDueReminders(firstRun.plusMinutes(4))).isEmpty();

        HealthReminder second = service.claimDueReminders(firstRun.plusMinutes(5)).getFirst();
        service.markFailed(second, new IllegalStateException(), firstRun.plusMinutes(5));
        assertThat(reminder.getNextRetryAt()).isEqualTo(firstRun.plusMinutes(15));

        HealthReminder third = service.claimDueReminders(firstRun.plusMinutes(15)).getFirst();
        service.markFailed(third, new IllegalStateException(), firstRun.plusMinutes(15));
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DEAD);
        assertThat(reminder.getEnabled()).isFalse();
        assertThat(service.claimDueReminders(firstRun.plusHours(2))).isEmpty();
    }

    @Test
    void concurrentSchedulersCanClaimOnlyOnce() throws Exception {
        InMemoryHealthReminderRepository repository = new InMemoryHealthReminderRepository();
        ReminderConfigService firstService = new ReminderConfigService(repository);
        ReminderConfigService secondService = new ReminderConfigService(repository);
        firstService.add("user-a", null, "喝水", 8, 30);
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 8, 30);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<HealthReminder>> first = executor.submit(() -> {
                start.await();
                return firstService.claimDueReminders(now);
            });
            Future<List<HealthReminder>> second = executor.submit(() -> {
                start.await();
                return secondService.claimDueReminders(now);
            });
            start.countDown();

            assertThat(first.get().size() + second.get().size()).isEqualTo(1);
        }
    }

    private ReminderConfigService service() {
        return new ReminderConfigService(new InMemoryHealthReminderRepository());
    }
}
