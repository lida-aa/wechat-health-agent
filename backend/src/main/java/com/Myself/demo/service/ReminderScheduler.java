package com.Myself.demo.service;

import com.Myself.demo.bot.ToolRegistry;
import com.Myself.demo.bot.WeChatBotService;
import com.Myself.demo.entity.HealthReminder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ReminderScheduler {

    private final ReminderConfigService configService;
    private final ToolRegistry toolRegistry;
    private final WeChatBotService weChatBotService;

    public ReminderScheduler(ReminderConfigService configService,
                             ToolRegistry toolRegistry,
                             WeChatBotService weChatBotService) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
        this.weChatBotService = weChatBotService;
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndPush() {
        LocalDateTime now = LocalDateTime.now();
        List<HealthReminder> dueList = configService.claimDueReminders(now);

        for (HealthReminder r : dueList) {
            try {
                String content;
                if (r.getToolName() == null) {
                    // 自定义文字提醒，直接使用 params 作为内容
                    content = r.getParams();
                } else {
                    // 调用工具获取内容
                    String result = toolRegistry.executeScheduled(
                            r.getToolName(), r.getParams(), r.getUserId());
                    content = (result != null) ? result : "工具执行失败: " + r.getToolName();
                }

                boolean sent = weChatBotService.sendReminder(r.getUserId(),
                        "【" + formatLabel(r) + "】\n" + content);
                if (!sent) {
                    throw new ReminderDeliveryException();
                }
                configService.markSucceeded(r, LocalDateTime.now());
                log.info("提醒已推送: reminderId={}, tool={}, time={}:{}",
                        r.getId(), r.getToolName(), r.getHour(), r.getMinute());
            } catch (Exception e) {
                configService.markFailed(r, e, LocalDateTime.now());
                log.error("提醒推送失败: reminderId={}, attempt={}, errorType={}",
                        r.getId(), r.getAttemptCount(), e.getClass().getSimpleName());
            }
        }
    }

    private static String formatLabel(HealthReminder r) {
        return String.format("每天 %02d:%02d", r.getHour(), r.getMinute());
    }

    private static final class ReminderDeliveryException extends RuntimeException {
    }
}
