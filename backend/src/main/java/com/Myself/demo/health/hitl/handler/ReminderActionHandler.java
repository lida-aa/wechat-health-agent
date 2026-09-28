package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.bot.HealthToolPolicy;
import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.health.hitl.ReminderCancellationDraft;
import com.Myself.demo.health.hitl.ReminderDraft;
import com.Myself.demo.service.ReminderConfigService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ReminderActionHandler implements HealthActionHandler {

    private final ReminderConfigService reminders;
    private final HealthToolPolicy toolPolicy;

    public ReminderActionHandler(ReminderConfigService reminders, HealthToolPolicy toolPolicy) {
        this.reminders = reminders;
        this.toolPolicy = toolPolicy;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(
                PendingHealthAction.Type.CREATE_REMINDER,
                PendingHealthAction.Type.CANCEL_REMINDER);
    }

    @Override
    public String execute(PendingHealthAction action) {
        return switch (action.type()) {
            case CREATE_REMINDER -> create(action);
            case CANCEL_REMINDER -> cancel(action);
            default -> throw new IllegalArgumentException("不支持的提醒动作: " + action.type());
        };
    }

    @Override
    public String render(PendingHealthAction action) {
        return switch (action.type()) {
            case CREATE_REMINDER -> renderCreate(action);
            case CANCEL_REMINDER -> renderCancellation(action);
            default -> throw new IllegalArgumentException("不支持的提醒动作: " + action.type());
        };
    }

    @Override
    public String label(PendingHealthAction action) {
        return action.type() == PendingHealthAction.Type.CREATE_REMINDER ? "提醒创建" : "提醒取消";
    }

    private String create(PendingHealthAction action) {
        ReminderDraft draft = action.payloadAs(ReminderDraft.class);
        if (draft.toolName() != null && !toolPolicy.canRunAutomatically(draft.toolName())) {
            throw new IllegalArgumentException("非只读工具不能自动执行");
        }
        HealthReminder reminder = reminders.add(
                action.userId(), draft.toolName(), draft.params(), draft.hour(), draft.minute());
        return String.format("已创建提醒 #%d：每天 %02d:%02d %s",
                reminder.getId(), reminder.getHour(), reminder.getMinute(),
                HealthActionText.display(draft.label(), "提醒"));
    }

    private String cancel(PendingHealthAction action) {
        ReminderCancellationDraft draft = action.payloadAs(ReminderCancellationDraft.class);
        HealthReminder removed = reminders.cancel(action.userId(), draft.reminderId());
        if (removed == null) {
            return "提醒 #" + draft.reminderId() + " 已不存在，无需重复取消。";
        }
        return String.format("已取消提醒 #%d：每天 %02d:%02d %s",
                removed.getId(), removed.getHour(), removed.getMinute(),
                HealthActionText.display(draft.label(), "提醒"));
    }

    private String renderCreate(PendingHealthAction action) {
        ReminderDraft draft = action.payloadAs(ReminderDraft.class);
        return "每日提醒草稿（尚未创建）\n\n"
                + String.format("时间：每天 %02d:%02d%n", draft.hour(), draft.minute())
                + "内容：" + HealthActionText.display(draft.label(), "提醒") + "\n"
                + "\n回复“确认”创建提醒，回复“取消”放弃。";
    }

    private String renderCancellation(PendingHealthAction action) {
        ReminderCancellationDraft draft = action.payloadAs(ReminderCancellationDraft.class);
        return "取消提醒草稿（尚未执行）\n\n"
                + String.format("提醒：#%d，每天 %02d:%02d %s%n",
                draft.reminderId(), draft.hour(), draft.minute(),
                HealthActionText.display(draft.label(), "提醒"))
                + "\n回复“确认”取消该提醒，回复“取消”保留该提醒。";
    }
}
