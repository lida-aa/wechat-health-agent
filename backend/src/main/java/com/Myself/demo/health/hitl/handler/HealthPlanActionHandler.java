package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.HealthPlanDraft;
import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.service.HealthManagementService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class HealthPlanActionHandler implements HealthActionHandler {

    private final HealthManagementService health;

    public HealthPlanActionHandler(HealthManagementService health) {
        this.health = health;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(PendingHealthAction.Type.CREATE_HEALTH_PLAN);
    }

    @Override
    public String execute(PendingHealthAction action) {
        HealthPlanDraft draft = action.payloadAs(HealthPlanDraft.class);
        return health.savePlan(action.userId(), draft.conditions(), draft.goal(), draft.content());
    }

    @Override
    public String render(PendingHealthAction action) {
        HealthPlanDraft draft = action.payloadAs(HealthPlanDraft.class);
        return "健康计划草稿（尚未保存）\n\n"
                + draft.content() + "\n"
                + "\n回复“确认”保存到健康档案，回复“取消”放弃。";
    }

    @Override
    public String label(PendingHealthAction action) {
        return "健康计划";
    }
}
