package com.Myself.demo.health.hitl;

import com.Myself.demo.bot.HealthToolPolicy;
import com.Myself.demo.health.hitl.handler.HealthActionHandlerRegistry;
import com.Myself.demo.health.hitl.handler.HealthPlanActionHandler;
import com.Myself.demo.health.hitl.handler.HealthReportActionHandler;
import com.Myself.demo.health.hitl.handler.MealActionHandler;
import com.Myself.demo.health.hitl.handler.MedicationActionHandler;
import com.Myself.demo.health.hitl.handler.ProfileActionHandler;
import com.Myself.demo.health.hitl.handler.ReminderActionHandler;
import com.Myself.demo.health.hitl.handler.VitalActionHandler;
import com.Myself.demo.service.HealthManagementService;
import com.Myself.demo.service.ReminderConfigService;
import com.Myself.demo.support.InMemoryHealthReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthActionCoordinatorTest {

    private MutableClock clock;
    private PendingHealthActionStore store;
    private HealthManagementService health;
    private ReminderConfigService reminders;
    private HealthActionAuditService audit;
    private HealthActionCoordinator coordinator;
    private HealthActionDraftService drafts;
    private HealthToolPolicy toolPolicy;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        store = new PendingHealthActionStore(clock, Duration.ofMinutes(15));
        health = mock(HealthManagementService.class);
        reminders = mock(ReminderConfigService.class);
        audit = mock(HealthActionAuditService.class);
        toolPolicy = new HealthToolPolicy();
        HealthActionHandlerRegistry handlers = handlersFor(reminders);
        coordinator = new HealthActionCoordinator(store, audit, handlers);
        drafts = new HealthActionDraftService(store, audit, handlers, reminders, toolPolicy);
    }

    @Test
    void confirmationWritesExactlyOnce() {
        when(health.recordMeal("user-a", "牛肉面一碗", 520, "午餐", "图片估算"))
                .thenReturn("已记录午餐");
        when(health.todayCalories("user-a")).thenReturn(520);

        drafts.createMealDraft("user-a", "牛肉面一碗", 520, "午餐", "图片估算");

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已记录午餐", "520"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已经保存过"));

        verify(health, times(1)).recordMeal("user-a", "牛肉面一碗", 520, "午餐", "图片估算");
        verify(audit, times(1)).draftCreated(org.mockito.ArgumentMatchers.any());
        verify(audit, times(1)).executionStarted(org.mockito.ArgumentMatchers.any());
        verify(audit, times(1)).completed(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(audit, times(1)).duplicateIgnored(org.mockito.ArgumentMatchers.any());
        assertThat(store.currentForUser("user-a")).get()
                .extracting(PendingHealthAction::status)
                .isEqualTo(PendingHealthAction.Status.COMPLETED);
    }

    @Test
    void anotherUserCannotConfirmTheDraft() {
        drafts.createMealDraft("user-a", "米饭和鸡肉", 480, "午餐", "");

        Optional<String> reply = coordinator.handleUserReply("user-b", "确认");

        assertThat(reply).isEmpty();
        verify(health, never()).recordMeal(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(store.currentForUser("user-a")).get()
                .extracting(PendingHealthAction::status)
                .isEqualTo(PendingHealthAction.Status.WAITING_CONFIRMATION);
    }

    @Test
    void cancellationCannotBeConfirmedLater() {
        drafts.createMealDraft("user-a", "燕麦和鸡蛋", 330, "早餐", "");

        assertThat(coordinator.handleUserReply("user-a", "取消"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("没有写入"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已经取消"));

        verify(health, never()).recordMeal(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(audit, times(1)).cancelled(org.mockito.ArgumentMatchers.argThat(
                action -> action.status() == PendingHealthAction.Status.CANCELLED));
        verify(audit, times(1)).duplicateIgnored(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mealTypeCanBeChangedBeforeConfirmation() {
        drafts.createMealDraft("user-a", "沙拉", 280, "午餐", "");

        assertThat(coordinator.handleUserReply("user-a", "改成晚餐"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("餐别：晚餐"));

        assertThat(store.currentForUser("user-a")).get()
                .extracting(action -> action.payloadAs(MealDraft.class).mealType())
                .isEqualTo("晚餐");
    }

    @Test
    void expiredDraftCannotBeExecuted() {
        drafts.createMealDraft("user-a", "水果", 120, "加餐", "");
        clock.advance(Duration.ofMinutes(16));

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("超过 15 分钟"));
        verify(health, never()).recordMeal(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(audit, times(1)).expired(org.mockito.ArgumentMatchers.argThat(
                action -> action.status() == PendingHealthAction.Status.EXPIRED));
    }

    @Test
    void vitalDraftRequiresConfirmationAndWritesOnce() {
        when(health.recordVital("user-a", "血压", "135/85", "今天早上"))
                .thenReturn("已记录血压：135/85");

        String draft = drafts.createVitalDraft("user-a", "血压", "135/85", "今天早上");
        assertThat(draft).contains("健康指标草稿", "血压", "135/85", "尚未保存");
        verify(health, never()).recordVital(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.handleUserReply("user-a", "确认保存"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已记录血压", "及时就医"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已经保存过"));

        verify(health, times(1)).recordVital("user-a", "血压", "135/85", "今天早上");
    }

    @Test
    void profileChangeRequiresConfirmation() {
        when(health.recordProfileFact("user-a", "过敏史", "青霉素"))
                .thenReturn("已更新健康档案：过敏史 = 青霉素");

        String draft = drafts.createProfileDraft("user-a", "过敏史", "青霉素");
        assertThat(draft).contains("健康档案修改草稿", "尚未保存", "青霉素");
        verify(health, never()).recordProfileFact(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已更新健康档案"));
        verify(health, times(1)).recordProfileFact("user-a", "过敏史", "青霉素");
    }

    @Test
    void cancellingMedicationCreatesNeitherRecordNorReminder() {
        drafts.createMedicationDraft("user-a", "二甲双胍", "1片", "08:00,20:00", 20);

        assertThat(coordinator.handleUserReply("user-a", "取消"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("没有写入"));

        verify(health, never()).addMedication(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(reminders, never()).add(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void confirmingMedicationCreatesRecordAndEveryExplicitReminderOnce() {
        when(health.addMedication("user-a", "二甲双胍", "1片", "08:00,20:00", 20))
                .thenReturn("用药档案已更新");

        String draft = drafts.createMedicationDraft(
                "user-a", "二甲双胍", "1片", "08:00,20:00", 20);
        assertThat(draft).contains("尚未保存、尚未创建提醒");

        assertThat(coordinator.handleUserReply("user-a", "确认保存"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("用药档案已更新", "2 个每日服药提醒"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已经保存过"));

        verify(health, times(1)).addMedication("user-a", "二甲双胍", "1片", "08:00,20:00", 20);
        verify(reminders, times(1)).add("user-a", null,
                "服用 二甲双胍（1片），请按医嘱服药。", 8, 0);
        verify(reminders, times(1)).add("user-a", null,
                "服用 二甲双胍（1片），请按医嘱服药。", 20, 0);
    }

    @Test
    void medicationLogRequiresConfirmationAndWritesOnce() {
        when(health.logMedication("user-a", "布洛芬", 1, "刚刚"))
                .thenReturn("服药打卡成功");

        String draft = drafts.createMedicationLogDraft("user-a", "布洛芬", "刚刚");
        assertThat(draft).contains("服药打卡草稿", "尚未保存");
        verify(health, never()).logMedication(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("服药打卡成功"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已经保存过"));
        verify(health, times(1)).logMedication("user-a", "布洛芬", 1, "刚刚");
    }

    @Test
    void failedExecutionIsAuditedAndCannotBeRetried() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(health.recordVital("user-a", "体重", "65kg", ""))
                .thenThrow(failure);

        drafts.createVitalDraft("user-a", "体重", "65kg", "");

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("执行失败"));
        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("不会自动重试"));

        verify(audit, times(1)).failed(
                org.mockito.ArgumentMatchers.argThat(
                        action -> action.status() == PendingHealthAction.Status.FAILED),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.same(failure));
        verify(health, times(1)).recordVital("user-a", "体重", "65kg", "");
    }

    @Test
    void healthReportAndPlanAreNotSavedBeforeConfirmation() {
        when(health.saveHealthReport("user-a", "血压偏高，建议复查"))
                .thenReturn("体检报告已归档");
        String reportDraft = drafts.createHealthReportDraft(
                "user-a", "血压偏高，建议复查");
        assertThat(reportDraft).contains("体检报告归档草稿", "尚未保存");
        verify(health, never()).saveHealthReport(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("体检报告已归档"));
        verify(health, times(1)).saveHealthReport("user-a", "血压偏高，建议复查");

        when(health.savePlan("user-a", "高血压", "控压", "少盐并规律运动"))
                .thenReturn("计划已保存");
        String planDraft = drafts.createHealthPlanDraft(
                "user-a", "高血压", "控压", "少盐并规律运动");
        assertThat(planDraft).contains("健康计划草稿", "尚未保存");
        verify(health, never()).savePlan(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("计划已保存"));
        verify(health, times(1)).savePlan(
                "user-a", "高血压", "控压", "少盐并规律运动");
    }

    @Test
    void reminderRequiresConfirmationAndRejectsAutomaticWriteTools() {
        ReminderConfigService actualReminders = new ReminderConfigService(
                new InMemoryHealthReminderRepository());
        HealthActionHandlerRegistry actualHandlers = handlersFor(actualReminders);
        HealthActionCoordinator actualCoordinator = new HealthActionCoordinator(
                store, audit, actualHandlers);
        HealthActionDraftService actualDrafts = new HealthActionDraftService(
                store, audit, actualHandlers, actualReminders, toolPolicy);

        String draft = actualDrafts.createReminderDraft(
                "user-a", "08:30", "daily_health_briefing", "{\"city\":\"杭州\"}");
        assertThat(draft).contains("每日提醒草稿", "尚未创建", "08:30");
        assertThat(actualReminders.list("user-a")).isEmpty();

        assertThat(actualCoordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已创建提醒"));
        assertThat(actualReminders.list("user-a")).singleElement().satisfies(reminder -> {
            assertThat(reminder.getToolName()).isEqualTo("daily_health_briefing");
            assertThat(reminder.getHour()).isEqualTo(8);
            assertThat(reminder.getMinute()).isEqualTo(30);
        });

        assertThat(actualDrafts.createReminderDraft(
                "user-b", "09:00", "add_medication", "{}"))
                .contains("不是只读工具", "不能设置为自动提醒");
        assertThat(store.currentForUser("user-b")).isEmpty();
        assertThat(actualDrafts.createReminderDraft(
                "user-c", "09:00", "", ""))
                .contains("提醒内容不能为空");
        assertThat(store.currentForUser("user-c")).isEmpty();
    }

    @Test
    void reminderCancellationRequiresConfirmation() {
        ReminderConfigService actualReminders = new ReminderConfigService(
                new InMemoryHealthReminderRepository());
        var reminder = actualReminders.add(
                "user-a", null, "喝水", 10, 0);
        HealthActionHandlerRegistry actualHandlers = handlersFor(actualReminders);
        HealthActionCoordinator actualCoordinator = new HealthActionCoordinator(
                store, audit, actualHandlers);
        HealthActionDraftService actualDrafts = new HealthActionDraftService(
                store, audit, actualHandlers, actualReminders, toolPolicy);

        String draft = actualDrafts.createReminderCancellationDraft("user-a", reminder.getId());
        assertThat(draft).contains("取消提醒草稿", "尚未执行", "#" + reminder.getId());
        assertThat(actualReminders.list("user-a")).hasSize(1);

        assertThat(actualCoordinator.handleUserReply("user-a", "确认"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("已取消提醒"));
        assertThat(actualReminders.list("user-a")).isEmpty();
    }

    private HealthActionHandlerRegistry handlersFor(ReminderConfigService reminderService) {
        return new HealthActionHandlerRegistry(List.of(
                new MealActionHandler(health),
                new VitalActionHandler(health),
                new ProfileActionHandler(health),
                new MedicationActionHandler(health, reminderService),
                new HealthReportActionHandler(health),
                new HealthPlanActionHandler(health),
                new ReminderActionHandler(reminderService, toolPolicy)));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
