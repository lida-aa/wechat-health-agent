package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.health.hitl.ProfileDraft;
import com.Myself.demo.service.HealthManagementService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ProfileActionHandler implements HealthActionHandler {

    private final HealthManagementService health;

    public ProfileActionHandler(HealthManagementService health) {
        this.health = health;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(PendingHealthAction.Type.UPDATE_PROFILE);
    }

    @Override
    public String execute(PendingHealthAction action) {
        ProfileDraft draft = action.payloadAs(ProfileDraft.class);
        return health.recordProfileFact(action.userId(), draft.key(), draft.value());
    }

    @Override
    public String render(PendingHealthAction action) {
        ProfileDraft draft = action.payloadAs(ProfileDraft.class);
        return "健康档案修改草稿（尚未保存）\n\n"
                + "字段：" + draft.key() + "\n"
                + "内容：" + draft.value() + "\n"
                + "\n回复“确认”保存，回复“取消”放弃。";
    }

    @Override
    public String label(PendingHealthAction action) {
        return "健康档案修改";
    }
}
