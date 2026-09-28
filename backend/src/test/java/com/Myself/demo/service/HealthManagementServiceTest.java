package com.Myself.demo.service;

import com.Myself.demo.health.service.HealthDietService;
import com.Myself.demo.health.service.HealthOverviewService;
import com.Myself.demo.health.service.HealthPlanService;
import com.Myself.demo.health.service.HealthProfileService;
import com.Myself.demo.health.service.HealthRecordStore;
import com.Myself.demo.health.service.HealthReportService;
import com.Myself.demo.health.service.HealthVitalService;
import com.Myself.demo.health.service.MedicationHealthService;
import com.Myself.demo.repository.HealthRecordRepository;
import com.Myself.demo.support.InMemoryHealthRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HealthManagementServiceTest {

    @Test
    void persistsVitalThroughSingleRepositoryAndSurvivesServiceRecreation() {
        InMemoryHealthRecordRepository repository = new InMemoryHealthRecordRepository();
        HealthManagementService first = service(repository);

        first.recordVital("user-a", "血压", "120/80", "今天 08:30");

        HealthManagementService recreated = service(repository);
        List<Map<String, Object>> vitals = list(recreated.record("user-a"), "vitals");
        assertThat(vitals).singleElement()
                .satisfies(vital -> {
                    assertThat(vital.get("type")).isEqualTo("血压");
                    assertThat(vital.get("value")).isEqualTo("120/80");
                });
    }

    @Test
    void isolatesHealthRecordsByCurrentUser() {
        InMemoryHealthRecordRepository repository = new InMemoryHealthRecordRepository();
        HealthManagementService service = service(repository);

        service.recordProfileFact("user-a", "过敏史", "青霉素");
        service.recordProfileFact("user-b", "运动习惯", "每周跑步");

        assertThat(profile(service.record("user-a")))
                .containsEntry("过敏史", "青霉素")
                .doesNotContainKey("运动习惯");
        assertThat(profile(service.record("user-b")))
                .containsEntry("运动习惯", "每周跑步")
                .doesNotContainKey("过敏史");
    }

    @Test
    void doesNotReportSuccessWhenCanonicalStoreFails() {
        HealthRecordRepository failingRepository = new HealthRecordRepository() {
            @Override
            public Map<String, Object> load(String userId) {
                return new LinkedHashMap<>();
            }

            @Override
            public void save(String userId, Map<String, Object> record) {
                throw new IllegalStateException("database unavailable");
            }
        };

        assertThatThrownBy(() -> service(failingRepository)
                .recordVital("user-a", "血压", "120/80", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
    }

    @Test
    void facadeKeepsMedicationDietReportAndPlanWorkflowCompatible() {
        HealthManagementService service = service(new InMemoryHealthRecordRepository());

        service.addMedication("user-a", "二甲双胍", "1片", "08:00", 20);
        service.logMedication("user-a", "二甲双胍", 1, "今天 08:00");
        service.recordMeal("user-a", "燕麦和鸡蛋", 350, "早餐", "估算值");
        service.saveHealthReport("user-a", "血压偏高，建议复查");
        service.savePlan("user-a", "高血压", "控压", "少盐并规律运动");

        assertThat(service.queryRemaining("user-a", "二甲双胍")).contains("19");
        assertThat(service.dailyDietarySummary("user-a")).contains("燕麦和鸡蛋", "350千卡");
        assertThat(service.interpretLatestReport("user-a")).contains("血压", "不能替代医生诊断");
        assertThat(service.richOverview("user-a"))
                .contains("体检报告：1 份", "健康方案：1 份", "服药记录：1 次");
    }

    private HealthManagementService service(HealthRecordRepository repository) {
        HealthRecordStore store = new HealthRecordStore(repository);
        HealthDietService diet = new HealthDietService(store);
        return new HealthManagementService(
                store,
                new MedicationHealthService(store),
                new HealthVitalService(store),
                new HealthProfileService(store),
                new HealthReportService(store),
                new HealthPlanService(store),
                diet,
                new HealthOverviewService(store, diet, mock(WeatherService.class)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> record, String key) {
        return (List<Map<String, Object>>) record.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> profile(Map<String, Object> record) {
        return (Map<String, Object>) record.get("profile");
    }
}
