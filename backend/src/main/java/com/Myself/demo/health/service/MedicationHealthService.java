package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.intValue;
import static com.Myself.demo.health.service.HealthRecordValues.list;
import static com.Myself.demo.health.service.HealthRecordValues.now;
import static com.Myself.demo.health.service.HealthRecordValues.safe;

@Service
public class MedicationHealthService {

    private final HealthRecordStore store;

    public MedicationHealthService(HealthRecordStore store) {
        this.store = store;
    }

    public String add(String userId, String name, String dosage, String schedule, int remaining) {
        return store.update(userId, data -> {
            List<Map<String, Object>> medications = list(data, "medications");
            Map<String, Object> medication = medications.stream()
                    .filter(item -> name != null && name.equalsIgnoreCase(String.valueOf(item.get("name"))))
                    .findFirst()
                    .orElseGet(() -> {
                        Map<String, Object> created = new LinkedHashMap<>();
                        medications.add(created);
                        return created;
                    });
            medication.put("name", name);
            medication.put("dosage", dosage);
            medication.put("schedule", schedule);
            medication.put("remaining", Math.max(0, remaining));
            medication.put("updatedAt", now());
            String warning = remaining <= 5
                    ? "\n\n余量预警：当前仅剩 " + Math.max(0, remaining) + "，请提前补充。" : "";
            return "用药档案已更新\n\n"
                    + "药品：" + safe(name, "未填写") + "\n"
                    + "每次用量：" + safe(dosage, "按医嘱") + "\n"
                    + "服药时间：" + safe(schedule, "未设置") + "\n"
                    + "当前余量：" + Math.max(0, remaining) + warning
                    + "\n\n请按医生或药师指导用药，不要自行调整剂量或停药。";
        });
    }

    public String log(String userId, String name, String takenAt) {
        return log(userId, name, 1, takenAt);
    }

    public String log(String userId, String name, int quantity, String takenAt) {
        return store.update(userId, data -> {
            List<Map<String, Object>> logs = list(data, "medicationLogs");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("takenAt", takenAt == null || takenAt.isBlank() ? now() : takenAt);
            if (quantity > 1) item.put("quantity", quantity);
            logs.add(item);

            Map<String, Object> medication = list(data, "medications").stream()
                    .filter(med -> name != null && name.equalsIgnoreCase(String.valueOf(med.get("name"))))
                    .findFirst().orElse(null);
            Integer remaining = null;
            if (medication != null) {
                remaining = Math.max(0, intValue(medication.get("remaining")) - quantity);
                medication.put("remaining", remaining);
                medication.put("updatedAt", now());
                Object currentDosage = medication.get("dosage");
                if (currentDosage == null || "未设置".equals(currentDosage)) {
                    medication.put("dosage", quantity + "粒");
                }
            } else if (name != null && !name.isEmpty() && !"未指定药物".equals(name)) {
                medication = new LinkedHashMap<>();
                medication.put("name", name);
                medication.put("dosage", quantity + "粒");
                medication.put("schedule", "未设置");
                medication.put("remaining", 0);
                medication.put("updatedAt", now());
                list(data, "medications").add(medication);
            }

            String result = "服药打卡成功\n\n药品：" + safe(name, "未填写")
                    + "\n时间：" + item.get("takenAt");
            if (quantity > 1) result += "\n数量：" + quantity + "粒";
            if (remaining != null) result += "\n剩余：" + remaining;
            if (remaining != null && remaining <= 5) result += "\n\n余量预警：请提前补药。";
            if (medication != null && remaining == null) {
                result += "\n\n" + name + "未在用药档案中登记，已自动加入名录。可发送「登记用药」填写用量和时段。";
            }
            return result;
        });
    }

    public String queryRemaining(String userId, String drugName) {
        return store.read(userId, data -> {
            for (Map<String, Object> medication : list(data, "medications")) {
                if (drugName.equalsIgnoreCase(String.valueOf(medication.get("name")))) {
                    int remaining = intValue(medication.get("remaining"));
                    String dosage = String.valueOf(medication.getOrDefault("dosage", ""));
                    StringBuilder result = new StringBuilder(drugName + "剩余情况\n\n")
                            .append("• 剩余数量：").append(remaining);
                    if (!dosage.isEmpty() && !"null".equals(dosage) && !"未设置".equals(dosage)) {
                        result.append("\n• 每次用量：").append(dosage);
                    }
                    if (remaining <= 5) result.append("\n\n余量预警：请提前补药。");
                    return result.toString();
                }
            }
            return drugName + " 尚未在用药档案中登记。";
        });
    }

    public String findLastLoggedName(String userId) {
        return store.read(userId, data -> {
            List<Map<String, Object>> logs = list(data, "medicationLogs");
            if (logs.isEmpty()) return null;
            String name = String.valueOf(logs.get(logs.size() - 1).get("name"));
            return name == null || name.isEmpty() || name.contains("未指定") ? null : name;
        });
    }
}
