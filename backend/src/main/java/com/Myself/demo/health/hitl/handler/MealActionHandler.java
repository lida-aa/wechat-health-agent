package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.MealDraft;
import com.Myself.demo.health.hitl.PendingHealthAction;
import com.Myself.demo.service.HealthManagementService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MealActionHandler implements HealthActionHandler {

    private final HealthManagementService health;

    public MealActionHandler(HealthManagementService health) {
        this.health = health;
    }

    @Override
    public Set<PendingHealthAction.Type> supportedTypes() {
        return Set.of(PendingHealthAction.Type.RECORD_MEAL);
    }

    @Override
    public String execute(PendingHealthAction action) {
        MealDraft draft = action.payloadAs(MealDraft.class);
        String saved = health.recordMeal(
                action.userId(), draft.foods(), draft.estimatedCalories(), draft.mealType(), draft.note());
        return saved + "\n今日累计摄入约 " + health.todayCalories(action.userId()) + " 千卡。"
                + "\n\n图片识别和热量均为估算值，仅用于日常健康管理。";
    }

    @Override
    public String render(PendingHealthAction action) {
        MealDraft draft = action.payloadAs(MealDraft.class);
        return "饮食记录草稿（尚未保存）\n\n"
                + "餐别：" + draft.mealType() + "\n"
                + "食物：" + draft.foods() + "\n"
                + "估算热量：" + draft.estimatedCalories() + " 千卡\n"
                + (draft.note().isBlank() ? "" : "备注：" + draft.note() + "\n")
                + "\n回复“确认”保存，回复“取消”放弃；也可以说“改成早餐/午餐/晚餐/加餐”。";
    }

    @Override
    public String label(PendingHealthAction action) {
        return "饮食";
    }
}
