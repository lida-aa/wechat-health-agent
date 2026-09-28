package com.Myself.demo.tool;

import com.Myself.demo.bot.CreatesHealthActionDraft;
import com.Myself.demo.bot.CurrentUserId;
import com.Myself.demo.health.hitl.HealthActionDraftService;
import com.Myself.demo.service.AmapService;
import com.Myself.demo.service.HealthManagementService;
import com.Myself.demo.service.PharmacyResponseFormatter;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WeChat-native health management commands. */
@Component
public class HealthManagementTool {
    private final HealthManagementService health;
    private final AmapService amap;
    private final PharmacyResponseFormatter pharmacyFormatter;
    private final HealthActionDraftService healthActions;
    private final String healthExamUrl;
    public HealthManagementTool(HealthManagementService health, AmapService amap,
                                PharmacyResponseFormatter pharmacyFormatter,
                                HealthActionDraftService healthActions,
                                @Value("${health.exam.url:}") String healthExamUrl) {
        this.health = health;
        this.amap = amap;
        this.pharmacyFormatter = pharmacyFormatter;
        this.healthActions = healthActions;
        this.healthExamUrl = healthExamUrl == null ? "" : healthExamUrl.trim();
    }

    @Tool(name = "health_overview", value = "查看用户的个人电子健康档案，包括登记用药、最近健康指标和服药记录。")
    public String overview(@CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return health.richOverview(userId);
    }

    @CreatesHealthActionDraft
    @Tool(name = "record_profile_fact", value = "为健康档案基础信息、生活习惯、过敏史、既往病史、家族史等修改创建待确认草稿，不直接写入。")
    public String recordProfileFact(@P("档案字段，例如睡眠、身高、年龄、过敏史、运动习惯") String key,
                                    @P("字段值") String value, @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createProfileDraft(userId, key, value);
    }

    @CreatesHealthActionDraft
    @Tool(name = "add_medication", value = "为药品和多时段服药计划创建待确认草稿；确认后才写入用药档案并创建明确时段的提醒。")
    public String addMedication(@P("药品名称") String name, @P("每次用量") String dosage,
                                @P("服药时段，例如早晚或 08:00,20:00") String schedule,
                                @P("剩余数量") int remaining, @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createMedicationDraft(userId, name, dosage, schedule, remaining);
    }

    @CreatesHealthActionDraft
    @Tool(name = "record_vital", value = "为血压、血糖、体重、体温等健康指标创建待确认草稿，不直接写入健康档案。")
    public String recordVital(@P("指标名称，例如血压或空腹血糖") String type, @P("指标值") String value,
                              @P("测量时间，仅当用户明确说出具体日期时间时才填写，例如「今天早上」「8月3日 20:30」，用户没说时间就留空，绝不要编造日期") String measuredAt,
                              @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createVitalDraft(userId, type, value, measuredAt);
    }

    @CreatesHealthActionDraft
    @Tool(name = "log_medication", value = "为一次服药打卡创建待确认草稿，不直接扣减余量或写入档案。")
    public String logMedication(@P("药品名称，例如阿莫西林、布洛芬、头孢克肟") String name, @P("服药时间，可留空") String takenAt,
                                @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createMedicationLogDraft(userId, name, takenAt);
    }

    public String logMedication(String name, int quantity, String userId) {
        return healthActions.createMedicationLogDraft(userId, name, quantity, "");
    }

    public String clearAllHealthData(String userId) {
        return health.clearAllHealthData(userId);
    }

    public String queryRemaining(String userId, String drugName) {
        return health.queryRemaining(userId, drugName);
    }

    @Tool(name = "find_pharmacy", value = "查询附近药店")
    public String findPharmacy(@P("城市") String city, @P("位置或地标") String location) {
        String keyword = (location != null && !location.isEmpty()) ? location + " 药店" : "药店";
        List<Map<String, Object>> pois = amap.searchPoi(city, keyword);
        if (!pois.isEmpty()) {
            StringBuilder out = new StringBuilder("【附近药店】\n");
            pois.stream().limit(5).forEach(p -> {
                out.append("- ").append(p.get("name")).append("：").append(p.get("address"));
                Object distance = p.get("distance");
                if (distance != null && !distance.toString().isBlank()) out.append("（约 ").append(distance).append(" 米）");
                out.append("\n");
            });
            return out.toString();
        }
        return "暂未查到附近药店，请确认城市名称或稍后重试。";
    }

    @Tool(name = "symptom_screening", value = "语音症状智能咨询的安全初筛入口。结合症状给出分级就医与护理建议，明确不能替代医生诊断。")
    public String symptomScreening(@P("症状、持续时间和严重程度") String symptoms) {
        return health.screenSymptoms(symptoms);
    }

    @Tool(name = "interpret_health_report", value = "归集体检报告文字并做风险关键词初筛，提示复核和就医方向，不进行确诊。")
    public String interpretReport(@P("体检报告文字或图片识别出的文字") String reportText) {
        return health.interpretReport(reportText);
    }

    @CreatesHealthActionDraft
    @Tool(name = "save_health_report", value = "为体检报告归档创建待确认草稿，确认后才写入当前用户的健康档案。")
    public String saveHealthReport(@P("体检报告文字或图片识别出的文字") String reportText,
                                   @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createHealthReportDraft(userId, reportText);
    }

    @Tool(name = "interpret_latest_health_report", value = "解读当前用户健康档案中最近一次保存的体检报告，给出复查和就医提示，不进行确诊。")
    public String interpretLatestReport(@CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return health.interpretLatestReport(userId);
    }

    @Tool(name = "open_health_exam", value = "提供已配置的线上体检预约入口。只返回项目配置的正式入口，不编造链接。")
    public String openHealthExam() {
        if (healthExamUrl.isBlank()) {
            return "线上体检入口尚未配置，请联系管理员设置 health.exam.url 后再试。";
        }
        return "线上体检预约\n\n入口：" + healthExamUrl
                + "\n\n预约前请核对体检机构、套餐内容、适用人群和退款规则。";
    }

    @CreatesHealthActionDraft
    @Tool(name = "create_health_plan", value = "生成饮食和运动干预建议草稿，确认后才保存到健康档案。")
    public String createPlan(@P("病症特征或慢病情况") String conditions, @P("健康目标，例如控糖或减重") String goal,
                             @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return healthActions.createHealthPlanDraft(
                userId, conditions, goal, health.plan(conditions, goal));
    }

    @Tool(name = "daily_health_briefing", value = "生成每日健康陪伴简报，整合用药清单、季节天气健康提醒和慢病科普方向，可配合每日提醒推送。")
    public String dailyBriefing(@P("城市") String city, @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        return health.dailyBriefingWithWeather(userId, city);
    }

    public String findLastLoggedMedicationName(String userId) {
        return health.findLastLoggedMedicationName(userId);
    }

    @CreatesHealthActionDraft
    @Tool(name = "schedule_health_briefing", value = "为每日健康陪伴推送创建待确认草稿，确认后才设置提醒。")
    public String scheduleBriefing(@P("提醒时间，HH:mm，例如 08:00") String time,
                                    @P("城市") String city, @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        String safeCity = city == null ? "" : city.replace("\\", "").replace("\"", "");
        return healthActions.createReminderDraft(
                userId,
                time,
                "daily_health_briefing",
                "{\"city\":\"" + safeCity + "\"}",
                "每日健康简报（城市：" + displayCity(safeCity) + "）");
    }

    @Tool(name = "today_diet_summary", value = "查看用户今天的饮食记录和总摄入热量。")
    public String todayDietSummary(@CurrentUserId @P("用户ID，系统自动填充") String userId) {
        String summary = health.dailyDietarySummary(userId);
        String profileHint = "";
        try {
            Map<String, Object> data = health.record(userId);
            Object profile = data.get("profile");
            if (profile instanceof Map && !((Map<?, ?>) profile).isEmpty()) {
                profileHint += "\n\n体质参考\n";
                for (Map.Entry<?, ?> e : ((Map<?, ?>) profile).entrySet()) {
                    profileHint += "• " + e.getKey() + "：" + e.getValue() + "\n";
                }
                profileHint += "请结合以上体质信息，在用户查看饮食记录时给出个性化改进建议。";
            }
        } catch (Exception ignored) { }
        return summary + profileHint;
    }

    @CreatesHealthActionDraft
    @Tool(name = "analyze_meal", value = "分析用户一餐的饮食并创建待确认草稿，不直接写入健康档案。必须提示用户确认、修改或取消。")
    public String analyzeMeal(@P("食物分析结果，包含食物名称、分量和估算热量") String foodAnalysis,
                              @P("餐别：早餐、午餐、晚餐、加餐") String mealType,
                              @CurrentUserId @P("用户ID，系统自动填充") String userId) {
        Map<String, Object> data = health.record(userId);
        Object profile = data.get("profile");
        int totalCal = extractTotalCalories(foodAnalysis);

        String foods = foodAnalysis.length() > 200 ? foodAnalysis.substring(0, 200) + "..." : foodAnalysis;
        String draftMessage = healthActions.createMealDraft(
                userId, foods, totalCal, mealType, "图片识别生成，分量和热量均为估算值");

        StringBuilder advice = new StringBuilder(draftMessage);

        if (profile instanceof Map && !((Map<?, ?>) profile).isEmpty()) {
            advice.append("\n\n个性化建议");
            for (Map.Entry<?, ?> e : ((Map<?, ?>) profile).entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase();
                String v = String.valueOf(e.getValue());
                if (k.contains("血压") || k.contains("高血压")) {
                    advice.append("\n• 你有高血压，注意控制盐分摄入，避免过咸和腌制食品。");
                } else if (k.contains("血糖") || k.contains("糖尿病")) {
                    advice.append("\n• 你有血糖问题，注意控制碳水化合物总量，优先选择低GI食物。");
                } else if (k.contains("体重") || k.contains("减重")) {
                    advice.append("\n• 你正在管理体重，建议控制总热量摄入，增加蔬菜比例。");
                } else if (k.contains("过敏")) {
                    advice.append("\n• 注意避免过敏源：").append(v);
                }
            }
        }

        return advice.toString();
    }

    private static final Pattern CAL_TOTAL = Pattern.compile("总(?:估算)?(?:热量|卡路里)[^0-9]{0,6}(\\d+)\\s*(?:千卡|大卡)");
    private static final Pattern CAL_UNIT = Pattern.compile("(\\d+)\\s*(?:千卡|大卡)");

    private int extractTotalCalories(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            Matcher total = CAL_TOTAL.matcher(text);
            if (total.find()) {
                return Integer.parseInt(total.group(1));
            }
            Matcher unit = CAL_UNIT.matcher(text);
            int sum = 0;
            int max = 0;
            while (unit.find()) {
                int v = Integer.parseInt(unit.group(1));
                sum += v;
                max = Math.max(max, v);
            }
            return max > 0 ? max : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String displayCity(String city) {
        return city == null || city.isBlank() ? "未设置" : city;
    }
}
