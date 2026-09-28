package com.Myself.demo.health.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.Myself.demo.health.service.HealthRecordValues.list;
import static com.Myself.demo.health.service.HealthRecordValues.now;
import static com.Myself.demo.health.service.HealthRecordValues.summary;

@Service
public class HealthReportService {

    private final HealthRecordStore store;

    public HealthReportService(HealthRecordStore store) {
        this.store = store;
    }

    public String screenSymptoms(String symptoms) {
        String text = symptoms == null ? "" : symptoms.trim();
        if (text.isEmpty()) return "请描述症状、持续时间和严重程度，我会先做健康筛查提示。";
        boolean urgent = containsAny(text, "胸痛", "呼吸困难", "意识不清", "大出血", "言语不清");
        if (urgent) {
            return "检测到可能需要紧急处理的症状（" + text
                    + "）。请立即拨打 120 或前往最近急诊，本提示不能替代医生诊断。";
        }
        return "初筛记录：" + text
                + "。建议关注体温、血压等变化；若症状持续、加重或影响日常活动，请尽快线上问诊或到医院就诊。";
    }

    public String interpret(String reportText) {
        String text = reportText == null ? "" : reportText.trim();
        if (text.isEmpty()) return "请发送体检报告文字或图片识别结果后再进行解读。";
        boolean abnormal = containsAny(text, "异常", "偏高", "偏低", "阳性", "↑", "↓");
        List<String> focus = new ArrayList<>();
        addIfContains(focus, text, "血糖", "血糖与糖代谢");
        addIfContains(focus, text, "糖化血红蛋白", "长期血糖水平");
        addIfContains(focus, text, "血压", "血压");
        addIfContains(focus, text, "胆固醇", "血脂");
        addIfContains(focus, text, "甘油三酯", "血脂");
        addIfContains(focus, text, "肝功能", "肝功能");
        addIfContains(focus, text, "肾功能", "肾功能");
        addIfContains(focus, text, "尿酸", "尿酸");
        StringBuilder out = new StringBuilder("体检报告初步解读\n\n")
                .append("结果：").append(abnormal ? "发现异常关键词，需要结合参考区间复核" : "当前文字中未识别到明确异常关键词");
        if (!focus.isEmpty()) out.append("\n关注项目：").append(String.join("、", focus.stream().distinct().toList()));
        return out.append("\n\n下一步\n")
                .append("1. 对照报告中的参考范围和复查建议\n")
                .append("2. 携带完整报告咨询医生，由医生结合症状和既往病史判断\n")
                .append("3. 若伴明显不适或指标被标注危急值，请及时就医\n\n")
                .append("本解读仅用于健康信息整理，不能替代医生诊断，也不能作为自行调整用药的依据。")
                .toString();
    }

    public String save(String userId, String reportText) {
        String cleaned = reportText == null ? "" : reportText.trim();
        if (cleaned.isEmpty()) return "未检测到报告内容，请发送体检报告文字或图片识别结果。";
        return store.update(userId, data -> {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("text", cleaned);
            report.put("hasAbnormalKeyword", containsAny(cleaned, "异常", "偏高", "偏低", "阳性", "↑", "↓", "危急值"));
            report.put("savedAt", now());
            list(data, "healthReports").add(report);
            return "体检报告已归档\n\n"
                    + "报告摘要：" + summary(cleaned, 60) + "\n"
                    + "归档时间：" + report.get("savedAt") + "\n\n"
                    + interpret(cleaned);
        });
    }

    public String interpretLatest(String userId) {
        return store.read(userId, data -> {
            List<Map<String, Object>> reports = list(data, "healthReports");
            if (reports.isEmpty()) return "个人健康档案中暂无体检报告，请先发送报告文字或图片。";
            return interpret(String.valueOf(reports.get(reports.size() - 1).get("text")));
        });
    }

    private void addIfContains(List<String> target, String text, String keyword, String label) {
        if (text.contains(keyword)) target.add(label);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
