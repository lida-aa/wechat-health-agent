package com.Myself.demo.tool;

import com.Myself.demo.bot.CreatesHealthActionDraft;
import com.Myself.demo.bot.CurrentUserId;
import com.Myself.demo.entity.HealthReminder;
import com.Myself.demo.health.hitl.HealthActionDraftService;
import com.Myself.demo.service.ReminderConfigService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderTool {

    private final ReminderConfigService configService;
    private final HealthActionDraftService healthActions;

    public ReminderTool(ReminderConfigService configService, HealthActionDraftService healthActions) {
        this.configService = configService;
        this.healthActions = healthActions;
    }

    @CreatesHealthActionDraft
    @Tool(name = "create_reminder", value = "创建每日定时提醒草稿，确认后才会真正创建；自动调用仅允许只读工具")
    public String createReminder(
            @P("提醒时间(HH:mm)") String time,
            @P("工具名称") String toolName,
            @P("参数或提醒文字") String params,
            @CurrentUserId @P("用户ID") String userId) {
        return healthActions.createReminderDraft(userId, time, toolName, params);
    }

    @Tool(name = "list_reminders", value = "列出所有每日提醒")
    public String listReminders(@CurrentUserId @P("用户ID") String userId) {
        List<HealthReminder> items = configService.list(userId);
        if (items.isEmpty()) {
            return "你还没有设置任何每日提醒。试试说「每天早上8点提醒我天气」";
        }
        StringBuilder sb = new StringBuilder("你的每日提醒（共").append(items.size()).append("项）\n");
        for (HealthReminder r : items) {
            String type = r.getToolName() != null
                    ? "工具「" + r.getToolName() + "」" : "文字「" + r.getParams() + "」";
            sb.append("\n").append(r.getId()).append(". ")
              .append(String.format("%02d:%02d", r.getHour(), r.getMinute()))
              .append(" ").append(type)
              .append(Boolean.TRUE.equals(r.getEnabled()) ? "" : " [已暂停]");
        }
        return sb.toString();
    }

    @CreatesHealthActionDraft
    @Tool(name = "cancel_reminder", value = "创建取消每日提醒的待确认草稿，确认后才会真正取消")
    public String cancelReminder(
            @P("提醒编号") int id,
            @CurrentUserId @P("用户ID") String userId) {
        return healthActions.createReminderCancellationDraft(userId, id);
    }
}
