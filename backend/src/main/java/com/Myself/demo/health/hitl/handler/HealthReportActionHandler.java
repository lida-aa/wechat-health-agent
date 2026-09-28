package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.HealthReportDraft;
import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.service.HealthManagementService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class HealthReportActionHandler implements HealthActionHandler {

    private final HealthManagementService health;

    public HealthReportActionHandler(HealthManagementService health) {
        this.health = health;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(PendingHealthAction.Type.SAVE_HEALTH_REPORT);
    }

    @Override
    public String execute(PendingHealthAction action) {
        HealthReportDraft draft = action.payloadAs(HealthReportDraft.class);
        return health.saveHealthReport(action.userId(), draft.reportText());
    }

    @Override
    public String render(PendingHealthAction action) {
        HealthReportDraft draft = action.payloadAs(HealthReportDraft.class);
        return "体检报告归档草稿（尚未保存）\n\n"
                + HealthActionText.preview(draft.reportText(), 240) + "\n"
                + "\n回复“确认”归档，回复“取消”放弃。报告解读不能代替医生诊断。";
    }

    @Override
    public String label(PendingHealthAction action) {
        return "体检报告归档";
    }
}
