package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.health.hitl.VitalDraft;
import com.Myself.demo.service.HealthManagementService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VitalActionHandler implements HealthActionHandler {

    private final HealthManagementService health;

    public VitalActionHandler(HealthManagementService health) {
        this.health = health;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(PendingHealthAction.Type.RECORD_VITAL);
    }

    @Override
    public String execute(PendingHealthAction action) {
        VitalDraft draft = action.payloadAs(VitalDraft.class);
        return health.recordVital(action.userId(), draft.type(), draft.value(), draft.measuredAt())
                + "\n\n单次测量不能代替医生判断；如数值明显异常或伴随不适，请及时就医。";
    }

    @Override
    public String render(PendingHealthAction action) {
        VitalDraft draft = action.payloadAs(VitalDraft.class);
        return "健康指标草稿（尚未保存）\n\n"
                + "指标：" + draft.type() + "\n"
                + "数值：" + draft.value() + "\n"
                + "测量时间：" + HealthActionText.display(
                draft.measuredAt(), "未指定，将按当前时间记录") + "\n"
                + "\n回复“确认”保存，回复“取消”放弃。";
    }

    @Override
    public String label(PendingHealthAction action) {
        return "健康指标";
    }
}
