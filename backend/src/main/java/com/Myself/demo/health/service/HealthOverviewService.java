package com.Myself.demo.health.service;

import com.Myself.demo.service.HealthTextFormatter;
import com.Myself.demo.service.SmartWeatherService;
import com.Myself.demo.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.intValue;
import static com.Myself.demo.health.service.HealthRecordValues.list;
import static com.Myself.demo.health.service.HealthRecordValues.safe;
import static com.Myself.demo.health.service.HealthRecordValues.summary;

@Slf4j
@Service
public class HealthOverviewService {

    private final HealthRecordStore store;
    private final HealthDietService dietService;
    private final WeatherService weatherService;

    public HealthOverviewService(
            HealthRecordStore store,
            HealthDietService dietService,
            WeatherService weatherService) {
        this.store = store;
        this.dietService = dietService;
        this.weatherService = weatherService;
    }

    public String overview(String userId) {
        return store.read(userId, data -> {
            List<Map<String, Object>> medications = list(data, "medications");
            List<Map<String, Object>> vitals = list(data, "vitals");
            StringBuilder out = new StringBuilder("个人健康档案\n\n"
                    + "说明：数据按用户隔离保存，仅作个人健康记录。\n")
                    .append("\n用药档案（").append(medications.size()).append(" 项）\n");
            for (Map<String, Object> medication : medications) {
                out.append("• ").append(medication.get("name")).append("：").append(medication.get("remaining"));
                if (intValue(medication.get("remaining")) <= 5) out.append(" [需补药]");
                out.append("\n");
            }
            if (!vitals.isEmpty()) {
                Map<String, Object> latest = vitals.get(vitals.size() - 1);
                out.append("\n近期指标\n• ").append(latest.get("type")).append("：")
                        .append(latest.get("value")).append("\n");
            }
            return out.toString();
        });
    }

    public String richOverview(String userId) {
        return store.read(userId, data -> {
            StringBuilder out = new StringBuilder(HealthTextFormatter.overview(
                    list(data, "medications"), list(data, "vitals")));
            Object profile = data.get("profile");
            if (profile instanceof Map<?, ?> facts && !facts.isEmpty()) {
                out.append("\n\n基础信息与生活习惯\n");
                facts.forEach((key, value) -> out.append("• ").append(key).append("：").append(value).append("\n"));
            }
            List<Map<String, Object>> reports = list(data, "healthReports");
            List<Map<String, Object>> plans = list(data, "healthPlans");
            List<Map<String, Object>> medicationLogs = list(data, "medicationLogs");
            List<Map<String, Object>> mealLogs = list(data, "mealLogs");
            out.append("\n体检报告：").append(reports.size()).append(" 份")
                    .append("  |  健康方案：").append(plans.size()).append(" 份")
                    .append("  |  服药记录：").append(medicationLogs.size()).append(" 次");
            if (!mealLogs.isEmpty()) {
                String today = LocalDate.now().toString();
                long count = mealLogs.stream().filter(meal -> today.equals(String.valueOf(meal.get("date")))).count();
                out.append("  |  今日饮食：").append(count).append(" 餐/约")
                        .append(dietService.todayCalories(data)).append("千卡");
            }
            if (!reports.isEmpty()) {
                Map<String, Object> latest = reports.get(reports.size() - 1);
                out.append("\n最近报告：").append(summary(latest.get("text"), 48))
                        .append("（").append(latest.get("savedAt")).append("）");
            }
            if (!plans.isEmpty()) {
                Map<String, Object> latest = plans.get(plans.size() - 1);
                out.append("\n最近目标：").append(safe(String.valueOf(latest.get("goal")), "保持健康"));
            }
            return out.toString();
        });
    }

    public String dailyBriefingWithWeather(String userId, String city) {
        String briefing = dailyBriefing(userId, city);
        if (city == null || city.isBlank()) return briefing;
        try {
            return briefing + "\n\n天气与季节提醒\n"
                    + SmartWeatherService.getHealthAdvice(weatherService.getWeather(city));
        } catch (Exception e) {
            log.warn("daily health weather lookup failed city={}", city, e);
            return briefing;
        }
    }

    public String dailyBriefing(String userId, String city) {
        return store.read(userId, data -> {
            List<Map<String, Object>> medications = list(data, "medications");
            List<Map<String, Object>> vitals = list(data, "vitals");
            StringBuilder out = new StringBuilder("今日健康简报  |  " + LocalDate.now() + "\n\n今日用药\n");
            if (medications.isEmpty()) out.append("暂无已登记用药\n");
            for (Map<String, Object> medication : medications) {
                int remaining = intValue(medication.get("remaining"));
                out.append("• ").append(medication.get("name")).append("  ")
                        .append(safe(String.valueOf(medication.get("schedule")), "未设置时段"))
                        .append("  余量 ").append(remaining);
                if (remaining <= 5) out.append("  [需补药]");
                out.append("\n");
            }
            if (!vitals.isEmpty()) {
                Map<String, Object> latest = vitals.get(vitals.size() - 1);
                out.append("\n最近指标\n• ").append(latest.get("type")).append("：")
                        .append(latest.get("value")).append("  ").append(latest.get("measuredAt")).append("\n");
            }
            return out.append("\n今日陪伴\n• ").append(chronicCareTip(data))
                    .append("\n• ").append(seasonalTip())
                    .append("\n• 城市：").append(city == null || city.isBlank() ? "未设置" : city)
                    .toString();
        });
    }

    private String chronicCareTip(Map<String, Object> data) {
        String profile = String.valueOf(data.getOrDefault("profile", ""));
        if (profile.contains("高血压")) return "按医嘱用药并在相近时段测量血压，记录连续趋势比单次结果更有意义。";
        if (profile.contains("糖尿病") || profile.contains("血糖")) {
            return "规律进餐和监测血糖，记录饮食、活动与血糖变化，复诊时一并提供。";
        }
        return "规律饮水、保证睡眠，并安排适量活动；身体不适时及时咨询专业人员。";
    }

    private String seasonalTip() {
        Month month = LocalDate.now().getMonth();
        if (month == Month.JUNE || month == Month.JULY || month == Month.AUGUST) {
            return "高温季节注意补水、防晒，避免在正午进行高强度户外运动。";
        }
        if (month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY) {
            return "低温季节注意保暖，慢病人群外出前关注气温变化。";
        }
        return "换季时注意温差，保持室内通风和规律作息。";
    }
}
