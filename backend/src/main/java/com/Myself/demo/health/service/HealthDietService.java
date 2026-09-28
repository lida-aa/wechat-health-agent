package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.intValue;
import static com.Myself.demo.health.service.HealthRecordValues.list;

@Service
public class HealthDietService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final HealthRecordStore store;

    public HealthDietService(HealthRecordStore store) {
        this.store = store;
    }

    public String record(String userId, String foods, int estimatedCalories, String mealType, String note) {
        return store.update(userId, data -> {
            Map<String, Object> meal = new LinkedHashMap<>();
            meal.put("date", LocalDate.now().toString());
            meal.put("time", LocalDateTime.now().format(TIME));
            meal.put("foods", foods);
            meal.put("estimatedCalories", estimatedCalories);
            meal.put("mealType", mealType == null || mealType.isEmpty() ? "正餐" : mealType);
            if (note != null && !note.isEmpty()) meal.put("note", note);
            list(data, "mealLogs").add(meal);
            return "已记录" + mealType + "：" + foods + "（约" + estimatedCalories + "千卡）";
        });
    }

    public int todayCalories(String userId) {
        return store.read(userId, this::todayCalories);
    }

    public List<Map<String, Object>> todayMeals(String userId) {
        return store.read(userId, this::todayMeals);
    }

    public String dailySummary(String userId) {
        return store.read(userId, data -> {
            List<Map<String, Object>> meals = todayMeals(data);
            if (meals.isEmpty()) return "今日暂无饮食记录。";
            StringBuilder result = new StringBuilder("今日饮食记录\n");
            for (Map<String, Object> meal : meals) {
                result.append("• ").append(meal.get("time")).append(" ")
                        .append(meal.get("mealType")).append("：")
                        .append(meal.get("foods")).append("（约")
                        .append(meal.get("estimatedCalories")).append("千卡）\n");
            }
            return result.append("今日合计：约").append(todayCalories(data)).append("千卡").toString();
        });
    }

    int todayCalories(Map<String, Object> data) {
        String today = LocalDate.now().toString();
        return list(data, "mealLogs").stream()
                .filter(meal -> today.equals(String.valueOf(meal.get("date"))))
                .mapToInt(meal -> intValue(meal.get("estimatedCalories")))
                .sum();
    }

    private List<Map<String, Object>> todayMeals(Map<String, Object> data) {
        String today = LocalDate.now().toString();
        return list(data, "mealLogs").stream()
                .filter(meal -> today.equals(String.valueOf(meal.get("date"))))
                .toList();
    }
}
