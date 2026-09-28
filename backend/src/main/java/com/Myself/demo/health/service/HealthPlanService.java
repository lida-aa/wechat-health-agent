package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.list;
import static com.Myself.demo.health.service.HealthRecordValues.now;

@Service
public class HealthPlanService {

    private final HealthRecordStore store;

    public HealthPlanService(HealthRecordStore store) {
        this.store = store;
    }

    public String save(String userId, String conditions, String goal, String content) {
        return store.update(userId, data -> {
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("conditions", conditions);
            plan.put("goal", goal);
            plan.put("content", content);
            plan.put("createdAt", now());
            list(data, "healthPlans").add(plan);
            return content + "\n\n已保存到个人健康档案。";
        });
    }

    public String create(String conditions, String goal) {
        String normalizedConditions = conditions == null || conditions.isBlank() ? "无特殊病症" : conditions;
        String normalizedGoal = goal == null || goal.isBlank() ? "保持健康" : goal;
        String searchText = (normalizedConditions + " " + normalizedGoal).toLowerCase(Locale.ROOT);
        String diet = "餐盘的一半安排非淀粉类蔬菜，四分之一优质蛋白，四分之一全谷物或薯类";
        String recipe = "早餐：无糖酸奶/牛奶配燕麦和鸡蛋；午餐：杂粮饭、清蒸鱼和两份蔬菜；晚餐：豆制品、菌菇蔬菜和少量主食";
        String exercise = "每周累计 150 分钟中等强度有氧活动，并安排 2 次力量练习，从能轻松完成的强度开始";
        if (containsAny(searchText, "高血压", "控压", "血压")) {
            diet = "采用少盐饮食，减少腌制品、加工肉和高钠调味料，优先新鲜蔬果、全谷物与低脂蛋白";
            recipe = "早餐：燕麦、鸡蛋和无糖奶；午餐：杂粮饭、清蒸鱼、绿叶菜；晚餐：豆腐菌菇汤和蔬菜，烹调少盐";
        } else if (containsAny(searchText, "糖尿病", "控糖", "血糖")) {
            diet = "规律三餐，控制精制糖和含糖饮料，主食优先全谷物并与蔬菜、蛋白质搭配";
            recipe = "早餐：全麦面包、鸡蛋和无糖奶；午餐：糙米、鸡胸肉和蔬菜；晚餐：杂豆、鱼虾和蔬菜";
        } else if (containsAny(searchText, "减重", "减肥", "体重")) {
            diet = "保持适度能量缺口，优先高纤维蔬菜和优质蛋白，减少油炸食品、甜饮和夜宵";
            recipe = "早餐：鸡蛋、无糖酸奶和水果；午餐：半碗杂粮饭、瘦肉和两份蔬菜；晚餐：豆腐或鱼虾配蔬菜";
            exercise = "从每周 4 次快走或骑行开始，每次 30 分钟，逐步加入 2 次全身力量训练并记录体重趋势";
        }
        return "个性化健康方案\n\n"
                + "健康特征：" + normalizedConditions + "\n目标：" + normalizedGoal + "\n\n"
                + "饮食规划\n" + diet + "。\n\n"
                + "一日食谱参考\n" + recipe + "。\n\n"
                + "运动安排\n" + exercise + "。\n\n"
                + "执行建议：每周回顾体重、血压或血糖趋势，根据身体反应调整活动量。"
                + "慢病、孕期或运动中出现不适时，请先咨询医生或专业人员。";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
