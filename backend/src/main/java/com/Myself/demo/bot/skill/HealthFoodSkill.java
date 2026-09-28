package com.Myself.demo.bot.skill;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class HealthFoodSkill implements Skill {

    @Override
    public String getName() { return "健康饮食"; }

    @Override
    public String getRule() {
        return "饮食与体征结论只能作为健康管理参考，不做疾病诊断。记录饮食、体征或档案前，先向用户展示结构化草稿并等待明确确认。";
    }

    @Override
    public List<String> getTools() {
        return List.of("body_fat_rate", "food_nutrition", "health_tip", "analyze_meal", "record_vital");
    }
}
