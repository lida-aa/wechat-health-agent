package com.Myself.demo.service;

import com.Myself.demo.util.FileUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Extracts structured health information from a user-provided image. */
@Slf4j
@Service
public class ImageService {

    private final String apiKey;

    public ImageService(@Value("${llm.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String recognizeImage(byte[] imageBytes, String question) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "抱歉，没有读取到图片内容";
        }

        Path tempFile = null;
        try {
            tempFile = FileUtil.createTempFile("health_image_", ".png", imageBytes);
            String prompt = question == null || question.isBlank() ? "客观提取图片中的健康信息" : question;

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Map.of("image", tempFile.toAbsolutePath().toString()),
                            Map.of("text", prompt)))
                    .build();
            MultiModalConversationParam request = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-vl-max")
                    .message(userMessage)
                    .build();

            MultiModalConversationResult response = new MultiModalConversation().call(request);
            String answer = extractTextResult(response);
            log.info("健康图片识别完成: imageBytes={}, promptChars={}, outputChars={}",
                    imageBytes.length, prompt.length(), answer.length());
            return answer;
        } catch (Exception e) {
            log.error("健康图片识别失败: imageBytes={}, errorType={}",
                    imageBytes.length, e.getClass().getSimpleName());
            return "抱歉，图片识别失败，请稍后再试";
        } finally {
            if (tempFile != null) {
                FileUtil.deleteTempFile(tempFile);
            }
        }
    }

    private String extractTextResult(MultiModalConversationResult result) {
        List<Map<String, Object>> content = result.getOutput()
                .getChoices().get(0)
                .getMessage().getContent();
        for (Map<String, Object> item : content) {
            Object text = item.get("text");
            if (text instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return "未识别出清晰内容";
    }
}
