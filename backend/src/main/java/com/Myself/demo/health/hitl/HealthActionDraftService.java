package com.Myself.demo.health.hitl;

import com.Myself.demo.bot.HealthToolPolicy;
import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.health.hitl.handler.HealthActionHandlerRegistry;
import com.Myself.demo.service.ReminderConfigService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates pending actions without executing their side effects. */
@Service
public class HealthActionDraftService {

    private static final Pattern EXACT_TIME = Pattern.compile(
            "^([01]?\\d|2[0-3]):([0-5]\\d)$");

    private final PendingHealthActionStore store;
    private final HealthActionAuditService audit;
    private final HealthActionHandlerRegistry handlers;
    private final ReminderConfigService reminders;
    private final HealthToolPolicy toolPolicy;

    public HealthActionDraftService(PendingHealthActionStore store,
                                    HealthActionAuditService audit,
                                    HealthActionHandlerRegistry handlers,
                                    ReminderConfigService reminders,
                                    HealthToolPolicy toolPolicy) {
        this.store = store;
        this.audit = audit;
        this.handlers = handlers;
        this.reminders = reminders;
        this.toolPolicy = toolPolicy;
    }

    public String createMealDraft(String userId, String foods, int estimatedCalories,
                                  String mealType, String note) {
        return register(store.createMealDraft(userId, foods, estimatedCalories, mealType, note));
    }

    public String createVitalDraft(String userId, String type, String value, String measuredAt) {
        return register(store.createVitalDraft(userId, type, value, measuredAt));
    }

    public String createProfileDraft(String userId, String key, String value) {
        return register(store.createProfileDraft(userId, key, value));
    }

    public String createMedicationDraft(String userId, String name, String dosage,
                                        String schedule, int remaining) {
        return register(store.createMedicationDraft(userId, name, dosage, schedule, remaining));
    }

    public String createMedicationLogDraft(String userId, String name, String takenAt) {
        return createMedicationLogDraft(userId, name, 1, takenAt);
    }

    public String createMedicationLogDraft(String userId, String name, int quantity, String takenAt) {
        return register(store.createMedicationLogDraft(userId, name, quantity, takenAt));
    }

    public String createHealthReportDraft(String userId, String reportText) {
        return register(store.createHealthReportDraft(userId, reportText));
    }

    public String createHealthPlanDraft(String userId, String conditions, String goal, String content) {
        return register(store.createHealthPlanDraft(userId, conditions, goal, content));
    }

    public String createReminderDraft(String userId, String time, String toolName, String params) {
        return createReminderDraft(userId, time, toolName, params, "");
    }

    public String createReminderDraft(String userId, String time, String toolName,
                                      String params, String label) {
        Matcher matcher = EXACT_TIME.matcher(time == null ? "" : time.trim());
        if (!matcher.matches()) {
            return "时间格式错误，请使用 HH:mm 格式，如 08:00。";
        }
        String normalizedTool = toolName == null || toolName.isBlank() ? null : toolName.trim();
        String normalizedParams = params == null ? "" : params.trim();
        if (normalizedTool == null && normalizedParams.isBlank()) {
            return "提醒内容不能为空。";
        }
        if (normalizedTool != null && !toolPolicy.canRunAutomatically(normalizedTool)) {
            return "该工具不是只读工具，不能设置为自动提醒。可以改为普通文字提醒。";
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        String normalizedLabel = label == null || label.isBlank()
                ? (normalizedTool == null
                ? preview(normalizedParams, 40)
                : "工具「" + normalizedTool + "」")
                : label.trim();
        return register(store.createReminderDraft(
                userId, hour, minute, normalizedTool, normalizedParams, normalizedLabel));
    }

    public String createReminderCancellationDraft(String userId, int reminderId) {
        Optional<HealthReminder> existing = reminders.list(userId).stream()
                .filter(reminder -> reminder.getId() == reminderId)
                .findFirst();
        if (existing.isEmpty()) {
            return "未找到编号为 " + reminderId + " 的提醒，请先查看提醒列表。";
        }
        HealthReminder reminder = existing.get();
        String label = reminder.getToolName() == null
                ? "自定义文字" : "工具「" + reminder.getToolName() + "」";
        return register(store.createReminderCancellationDraft(
                userId, reminder.getId(), reminder.getHour(), reminder.getMinute(), label));
    }

    private String register(PendingHealthAction action) {
        audit.draftCreated(action);
        return handlers.render(action);
    }

    private String preview(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
