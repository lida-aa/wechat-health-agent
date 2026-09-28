package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.MedicationDraft;
import com.Myself.demo.health.hitl.MedicationLogDraft;
import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.service.HealthManagementService;
import com.Myself.demo.service.ReminderConfigService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MedicationActionHandler implements HealthActionHandler {

    private static final Pattern TIME = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

    private final HealthManagementService health;
    private final ReminderConfigService reminders;

    public MedicationActionHandler(HealthManagementService health, ReminderConfigService reminders) {
        this.health = health;
        this.reminders = reminders;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(
                PendingHealthAction.Type.ADD_MEDICATION,
                PendingHealthAction.Type.LOG_MEDICATION);
    }

    @Override
    public String execute(PendingHealthAction action) {
        return switch (action.type()) {
            case ADD_MEDICATION -> addMedication(action);
            case LOG_MEDICATION -> logMedication(action);
            default -> throw new IllegalArgumentException("不支持的用药动作: " + action.type());
        };
    }

    @Override
    public String render(PendingHealthAction action) {
        return switch (action.type()) {
            case ADD_MEDICATION -> renderMedication(action);
            case LOG_MEDICATION -> renderMedicationLog(action);
            default -> throw new IllegalArgumentException("不支持的用药动作: " + action.type());
        };
    }

    @Override
    public String label(PendingHealthAction action) {
        return action.type() == PendingHealthAction.Type.ADD_MEDICATION ? "用药登记" : "服药打卡";
    }

    private String addMedication(PendingHealthAction action) {
        MedicationDraft draft = action.payloadAs(MedicationDraft.class);
        String result = health.addMedication(
                action.userId(), draft.name(), draft.dosage(), draft.schedule(), draft.remaining());
        Matcher matcher = TIME.matcher(draft.schedule());
        int reminderCount = 0;
        while (matcher.find()) {
            reminders.add(
                    action.userId(),
                    null,
                    "服用 " + draft.name() + "（"
                            + HealthActionText.display(draft.dosage(), "按医嘱") + "），请按医嘱服药。",
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
            reminderCount++;
        }
        return reminderCount == 0
                ? result
                : result + " 已按时段创建 " + reminderCount + " 个每日服药提醒。";
    }

    private String logMedication(PendingHealthAction action) {
        MedicationLogDraft draft = action.payloadAs(MedicationLogDraft.class);
        return health.logMedication(action.userId(), draft.name(), draft.quantity(), draft.takenAt());
    }

    private String renderMedication(PendingHealthAction action) {
        MedicationDraft draft = action.payloadAs(MedicationDraft.class);
        return "用药登记草稿（尚未保存、尚未创建提醒）\n\n"
                + "药品：" + draft.name() + "\n"
                + "每次用量：" + HealthActionText.display(draft.dosage(), "未填写") + "\n"
                + "服药时段：" + HealthActionText.display(draft.schedule(), "未设置") + "\n"
                + "当前余量：" + draft.remaining() + "\n"
                + "\n回复“确认”后才会保存并按明确的 HH:mm 时段创建提醒；回复“取消”放弃。";
    }

    private String renderMedicationLog(PendingHealthAction action) {
        MedicationLogDraft draft = action.payloadAs(MedicationLogDraft.class);
        return "服药打卡草稿（尚未保存）\n\n"
                + "药品：" + draft.name() + "\n"
                + "数量：" + draft.quantity() + "\n"
                + "服药时间：" + HealthActionText.display(draft.takenAt(), "当前时间") + "\n"
                + "\n回复“确认”保存，回复“取消”放弃。";
    }
}
