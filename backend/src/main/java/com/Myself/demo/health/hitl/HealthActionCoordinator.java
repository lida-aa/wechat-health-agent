package com.Myself.demo.health.hitl;

import com.Myself.demo.health.hitl.handler.HealthActionHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/** Coordinates HITL state transitions; domain work lives in action handlers. */
@Slf4j
@Service
public class HealthActionCoordinator {

    private static final Set<String> CONFIRM_WORDS = Set.of(
            "确认", "确认记录", "确认保存", "保存", "保存记录", "没问题", "可以", "好的", "好");
    private static final Set<String> CANCEL_WORDS = Set.of(
            "取消", "取消记录", "不保存", "不要了", "算了");

    private final PendingHealthActionStore store;
    private final HealthActionAuditService audit;
    private final HealthActionHandlerRegistry handlers;

    public HealthActionCoordinator(PendingHealthActionStore store,
                                   HealthActionAuditService audit,
                                   HealthActionHandlerRegistry handlers) {
        this.store = store;
        this.audit = audit;
        this.handlers = handlers;
    }

    public Optional<String> handleUserReply(String userId, String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        Optional<PendingHealthAction> currentOptional = store.currentForUser(userId);
        if (currentOptional.isEmpty()) return Optional.empty();

        PendingHealthAction action = currentOptional.get();
        String command = normalizeCommand(text);
        boolean confirm = CONFIRM_WORDS.contains(command);
        boolean cancel = CANCEL_WORDS.contains(command);
        String changedMealType = action.type() == PendingHealthAction.Type.RECORD_MEAL
                ? extractMealTypeChange(text) : null;
        String label = handlers.label(action);

        Optional<String> terminalReply = handleTerminalState(action, confirm, cancel, label);
        if (terminalReply.isPresent()) return terminalReply;
        if (action.status() != PendingHealthAction.Status.WAITING_CONFIRMATION) {
            return Optional.empty();
        }

        if (cancel) {
            store.cancel(userId, action.actionId()).ifPresent(audit::cancelled);
            return Optional.of("已取消这条" + label + "草稿，没有写入或修改任何数据。");
        }

        if (changedMealType != null) {
            return store.updateMealType(userId, action.actionId(), changedMealType)
                    .map(updated -> {
                        audit.draftUpdated(updated);
                        return "已修改餐别。\n\n" + handlers.render(updated);
                    });
        }

        if (!confirm) return Optional.empty();
        return Optional.of(executeConfirmed(action, label));
    }

    private Optional<String> handleTerminalState(PendingHealthAction action,
                                                 boolean confirm,
                                                 boolean cancel,
                                                 String label) {
        if (action.status() == PendingHealthAction.Status.EXPIRED && (confirm || cancel)) {
            audit.expired(action);
            return Optional.of("这条" + label + "草稿已经超过 15 分钟，不能继续执行。请重新生成草稿。");
        }
        if (action.status() == PendingHealthAction.Status.COMPLETED && confirm) {
            audit.duplicateIgnored(action);
            return Optional.of("这条" + label + "记录已经保存过了，不会重复写入。");
        }
        if (action.status() == PendingHealthAction.Status.CANCELLED && confirm) {
            audit.duplicateIgnored(action);
            return Optional.of("这条" + label + "草稿已经取消，不能再次确认。请重新生成草稿。");
        }
        if (action.status() == PendingHealthAction.Status.FAILED && confirm) {
            audit.duplicateIgnored(action);
            return Optional.of("上次保存失败，这条草稿不会自动重试。请重新生成后再确认。");
        }
        return Optional.empty();
    }

    private String executeConfirmed(PendingHealthAction action, String label) {
        Optional<PendingHealthAction> claimed = store.claimForExecution(
                action.userId(), action.actionId());
        if (claimed.isEmpty()) {
            audit.duplicateIgnored(action);
            return "这条" + label + "草稿正在处理或已经处理，请勿重复确认。";
        }

        PendingHealthAction executing = claimed.get();
        audit.executionStarted(executing);
        long startedAtNanos = System.nanoTime();
        try {
            String result = handlers.execute(executing);
            store.markCompleted(action.userId(), action.actionId())
                    .ifPresent(completed -> audit.completed(completed, elapsedMillis(startedAtNanos)));
            return result;
        } catch (Exception e) {
            store.markFailed(action.userId(), action.actionId())
                    .ifPresent(failed -> audit.failed(failed, elapsedMillis(startedAtNanos), e));
            log.error("确认健康草稿失败: actionId={}, type={}, errorType={}",
                    action.actionId(), action.type(), e.getClass().getSimpleName());
            return label + "执行失败，没有自动重试。请稍后重新生成草稿。";
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private String normalizeCommand(String text) {
        return text.trim()
                .replace("。", "")
                .replace("！", "")
                .replace("!", "")
                .replace("，", "")
                .replace(",", "")
                .replace(" ", "");
    }

    private String extractMealTypeChange(String text) {
        String normalized = text.replace(" ", "");
        if (!(normalized.contains("改成") || normalized.contains("修改为") || normalized.contains("餐别"))) {
            return null;
        }
        if (normalized.contains("早餐") || normalized.contains("早饭")) return "早餐";
        if (normalized.contains("午餐") || normalized.contains("午饭")) return "午餐";
        if (normalized.contains("晚餐") || normalized.contains("晚饭")) return "晚餐";
        if (normalized.contains("加餐") || normalized.contains("夜宵")) return "加餐";
        return null;
    }
}
