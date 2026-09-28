package com.Myself.demo.service;

import com.Myself.demo.health.service.HealthDietService;
import com.Myself.demo.health.service.HealthOverviewService;
import com.Myself.demo.health.service.HealthPlanService;
import com.Myself.demo.health.service.HealthProfileService;
import com.Myself.demo.health.service.HealthRecordStore;
import com.Myself.demo.health.service.HealthReportService;
import com.Myself.demo.health.service.HealthVitalService;
import com.Myself.demo.health.service.MedicationHealthService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Stable facade for tools and HITL handlers; domain behavior lives in focused services. */
@Service
public class HealthManagementService {

    private final HealthRecordStore store;
    private final MedicationHealthService medications;
    private final HealthVitalService vitals;
    private final HealthProfileService profiles;
    private final HealthReportService reports;
    private final HealthPlanService plans;
    private final HealthDietService diet;
    private final HealthOverviewService overview;

    public HealthManagementService(
            HealthRecordStore store,
            MedicationHealthService medications,
            HealthVitalService vitals,
            HealthProfileService profiles,
            HealthReportService reports,
            HealthPlanService plans,
            HealthDietService diet,
            HealthOverviewService overview) {
        this.store = store;
        this.medications = medications;
        this.vitals = vitals;
        this.profiles = profiles;
        this.reports = reports;
        this.plans = plans;
        this.diet = diet;
        this.overview = overview;
    }

    public Map<String, Object> record(String userId) {
        return store.snapshot(userId);
    }

    public String addMedication(String userId, String name, String dosage, String schedule, int remaining) {
        return medications.add(userId, name, dosage, schedule, remaining);
    }

    public String recordVital(String userId, String type, String value, String measuredAt) {
        return vitals.record(userId, type, value, measuredAt);
    }

    public String logMedication(String userId, String name, String takenAt) {
        return medications.log(userId, name, takenAt);
    }

    public String logMedication(String userId, String name, int quantity, String takenAt) {
        return medications.log(userId, name, quantity, takenAt);
    }

    public String overview(String userId) {
        return overview.overview(userId);
    }

    public String richOverview(String userId) {
        return overview.richOverview(userId);
    }

    public String queryRemaining(String userId, String drugName) {
        return medications.queryRemaining(userId, drugName);
    }

    public String recordProfileFact(String userId, String key, String value) {
        return profiles.recordFact(userId, key, value);
    }

    public String screenSymptoms(String symptoms) {
        return reports.screenSymptoms(symptoms);
    }

    public String interpretReport(String reportText) {
        return reports.interpret(reportText);
    }

    public String saveHealthReport(String userId, String reportText) {
        return reports.save(userId, reportText);
    }

    public String interpretLatestReport(String userId) {
        return reports.interpretLatest(userId);
    }

    public String clearAllHealthData(String userId) {
        store.clear(userId);
        return "健康档案已清空\n\n"
                + "所有用药记录、健康指标、体检报告、健康方案和个人信息已全部删除。\n"
                + "可重新开始登记用药和记录健康数据。";
    }

    public String savePlan(String userId, String conditions, String goal, String content) {
        return plans.save(userId, conditions, goal, content);
    }

    public String plan(String conditions, String goal) {
        return plans.create(conditions, goal);
    }

    public String dailyBriefingWithWeather(String userId, String city) {
        return overview.dailyBriefingWithWeather(userId, city);
    }

    public String dailyBriefing(String userId, String city) {
        return overview.dailyBriefing(userId, city);
    }

    public String recordMeal(
            String userId, String foods, int estimatedCalories, String mealType, String note) {
        return diet.record(userId, foods, estimatedCalories, mealType, note);
    }

    public int todayCalories(String userId) {
        return diet.todayCalories(userId);
    }

    public List<Map<String, Object>> todayMeals(String userId) {
        return diet.todayMeals(userId);
    }

    public String dailyDietarySummary(String userId) {
        return diet.dailySummary(userId);
    }

    public String findLastLoggedMedicationName(String userId) {
        return medications.findLastLoggedName(userId);
    }
}
