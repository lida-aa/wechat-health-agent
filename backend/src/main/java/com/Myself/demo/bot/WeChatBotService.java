package com.Myself.demo.bot;

import com.Myself.demo.health.hitl.HealthActionCoordinator;
import com.Myself.demo.service.ImageService;
import com.Myself.demo.util.TimeUtil;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WeChat channel adapter for the health agent. */
@Slf4j
@Service
public class WeChatBotService {

    private static final long IMAGE_BUFFER_TTL = 5 * 60 * 1000L;
    private static final long MEAL_LOCK_TTL = 2 * 60 * 1000L;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private static final String IMAGE_RECOGNITION_PROMPT = "请判断这张图片是否为食物/餐食照片。\n"
            + "如果是食物照片：请以【食物】开头，并逐一列出食物名称、估算分量、单项热量、总热量和营养评价。\n"
            + "如果不是食物照片：请客观提取与健康相关的可见内容，包括报告项目、数值、单位和参考范围。"
            + "不要猜测看不清的内容，不要给出诊断结论，也不要声称内容已经保存。";

    private static final String MEAL_PROMPT = "请严格按照以下格式分析这张食物照片：\n"
            + "1. 逐一列出识别到的食物名称和估算分量\n"
            + "2. 估算每种食物的热量（千卡）\n"
            + "3. 给出这餐的总估算热量\n"
            + "4. 简要评价这餐的营养搭配\n"
            + "只输出分析结果，不要额外寒暄。";

    private final CommandRouter commandRouter;
    private final ImageService imageService;
    private final BotManager botManager;
    private final WeChatClientRegistry clientRegistry;
    private final HealthActionCoordinator healthActionCoordinator;

    private final ThreadLocal<String> currentAccountId = new ThreadLocal<>();
    private final ConcurrentHashMap<String, List<ImageEntry>> imageBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> mealAnalysisLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> processedMessages = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(16);

    public WeChatBotService(CommandRouter commandRouter,
                            ImageService imageService,
                            BotManager botManager,
                            WeChatClientRegistry clientRegistry,
                            HealthActionCoordinator healthActionCoordinator) {
        this.commandRouter = commandRouter;
        this.imageService = imageService;
        this.botManager = botManager;
        this.clientRegistry = clientRegistry;
        this.healthActionCoordinator = healthActionCoordinator;
    }

    public void start() {
        botManager.setMessageHandler(this::handleMessages);
        botManager.start("main");
    }

    public void startBot(String name) {
        botManager.start(name);
    }

    private void handleMessages(List<WeixinMessage> messages, String accountId) {
        for (WeixinMessage message : messages) {
            if (processedMessages.putIfAbsent(message.getMessage_id(), Boolean.TRUE) != null) {
                continue;
            }
            executor.submit(() -> {
                currentAccountId.set(accountId);
                try {
                    handleMessage(message);
                } finally {
                    currentAccountId.remove();
                }
            });
        }
        if (processedMessages.size() > 2_000) {
            processedMessages.clear();
        }
    }

    private void handleMessage(WeixinMessage message) {
        if (message == null || message.getItem_list() == null) {
            return;
        }

        String userId = message.getFrom_user_id();
        MessageItem voiceItem = extractVoice(message);
        if (voiceItem != null) {
            handleVoiceMessage(userId, voiceItem);
            return;
        }

        if (extractFile(message) != null) {
            sendReply(userId, "当前健康助手不处理通用文件。体检报告可以发送清晰图片，或直接粘贴关键指标和参考范围。");
            return;
        }

        MessageItem imageItem = extractImage(message);
        if (imageItem != null) {
            handleImageMessage(userId, imageItem, extractText(message));
            return;
        }

        String text = extractText(message);
        if (text == null || text.isBlank()) {
            return;
        }
        log.info("收到微信健康文本消息: inputChars={}", text.length());

        var hitlReply = healthActionCoordinator.handleUserReply(userId, text);
        if (hitlReply.isPresent()) {
            sendReply(userId, hitlReply.get());
            return;
        }

        List<ImageEntry> images = getValidImages(userId);
        if (!images.isEmpty() && isMealRequest(text)) {
            handleMealImageMessage(userId, images, text);
            return;
        }

        if (!images.isEmpty()) {
            sendReply(userId, commandRouter.route(buildImageContext(images, text), userId));
            return;
        }

        if (isMealRequest(text)) {
            sendReply(userId, "请先发送一张食物照片。我会识别食物并估算热量，记录前仍会请你确认。");
            return;
        }

        sendReply(userId, commandRouter.route(text, userId));
    }

    private void handleVoiceMessage(String userId, MessageItem voiceItem) {
        String transcript = voiceItem.getVoice_item().getText();
        if (transcript == null || transcript.isBlank()) {
            sendReply(userId, "这段语音暂时没有识别出文字，可以再说一次或改用文字输入。");
            return;
        }
        log.info("收到微信健康语音消息: transcriptChars={}", transcript.length());

        var hitlReply = healthActionCoordinator.handleUserReply(userId, transcript);
        if (hitlReply.isPresent()) {
            sendReply(userId, hitlReply.get());
            return;
        }
        sendReply(userId, commandRouter.route(transcript, userId));
    }

    private void handleImageMessage(String userId, MessageItem imageItem, String caption) {
        try {
            byte[] imageBytes = requireClient().downloadImageFromMessageItem(imageItem);
            if (imageBytes == null || imageBytes.length == 0) {
                sendReply(userId, "没有读取到图片内容，请重新发送。");
                return;
            }
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                sendReply(userId, "图片超过 10MB，请压缩后重新发送。");
                return;
            }

            String userText = caption == null ? "" : caption.trim();
            log.info("收到微信健康图片消息: bytes={}, captionChars={}", imageBytes.length, userText.length());
            if (!userText.isEmpty() && isMealRequest(userText)) {
                runMealAnalysis(userId, imageBytes, userText, null);
                return;
            }

            String description = imageService.recognizeImage(imageBytes, IMAGE_RECOGNITION_PROMPT);
            addImageToBuffer(userId, imageBytes, description);

            if (isFoodDescription(description)) {
                runMealAnalysis(userId, imageBytes, userText, description);
                return;
            }

            if (!userText.isEmpty()) {
                sendReply(userId, commandRouter.route(buildImageContext(getValidImages(userId), userText), userId));
                return;
            }

            String cleanedDescription = stripFoodJudgement(description);
            int imageCount = getValidImages(userId).size();
            String suffix = imageCount < MAX_IMAGES
                    ? "\n\n你可以继续发送图片，或告诉我需要解读还是保存到健康档案。"
                    : "\n\n已达到最多 " + MAX_IMAGES + " 张图片，请告诉我需要解读还是保存到健康档案。";
            sendReply(userId, "📷 已提取图片中的健康信息：\n\n" + cleanedDescription + suffix);
        } catch (Exception e) {
            log.error("处理健康图片消息失败", e);
            sendReply(userId, "抱歉，图片处理失败，请稍后再试。");
        }
    }

    private void handleMealImageMessage(String userId, List<ImageEntry> images, String text) {
        ImageEntry latest = images.get(images.size() - 1);
        String recognition = latest.description;
        if (recognition == null || !isFoodDescription(recognition)) {
            try {
                recognition = imageService.recognizeImage(latest.bytes, MEAL_PROMPT);
                latest.description = recognition;
            } catch (Exception e) {
                log.warn("餐食图片重新识别失败", e);
            }
        }
        runMealAnalysis(userId, latest.bytes, text, recognition);
    }

    private boolean runMealAnalysis(String userId, byte[] imageBytes, String userText, String existingRecognition) {
        if (!tryAcquireMealAnalysis(userId)) {
            sendReply(userId, "这张餐食照片正在处理中，请不要重复发送，稍后查看结果即可。");
            return false;
        }

        String recognition = existingRecognition;
        if (recognition == null || recognition.isBlank()) {
            try {
                recognition = imageService.recognizeImage(imageBytes, MEAL_PROMPT);
            } catch (Exception e) {
                log.error("餐食图片识别失败", e);
                mealAnalysisLocks.remove(userId);
                sendReply(userId, "暂时无法识别这张餐食照片，请换一张清晰照片再试。");
                return false;
            }
        }

        String description = userText == null || userText.isBlank()
                ? ""
                : "用户补充说明：" + userText.trim() + "。\n";
        String agentInput = "用户发来一张餐食照片。\n"
                + description
                + "图像识别结果：\n" + recognition
                + "\n请调用 analyze_meal 工具分析，并创建待确认草稿；不得声称已经写入健康档案。"
                + "餐别从用户说明中推断。回复需要列出食物、估算分量、单项热量、总热量和可执行建议，"
                + "不要使用 Markdown 表格。";
        String result = commandRouter.route(agentInput, userId);
        clearImageBuffer(userId);
        sendReply(userId, result);
        return true;
    }

    private boolean tryAcquireMealAnalysis(String userId) {
        long now = TimeUtil.nowMillis();
        Long previous = mealAnalysisLocks.putIfAbsent(userId, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < MEAL_LOCK_TTL) {
            return false;
        }
        return mealAnalysisLocks.replace(userId, previous, now);
    }

    private boolean isMealRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim();
        if (containsAny(normalized, "药", "打卡", "服药")) {
            return false;
        }
        if (normalized.length() > 30 && !containsAny(normalized, "吃", "餐", "饭")) {
            return false;
        }
        return containsAny(normalized, "早餐", "午餐", "晚餐", "早饭", "午饭", "晚饭", "吃了", "吃的",
                "在吃", "这餐", "这顿饭", "饭菜", "餐食", "记录饮食", "卡路里", "热量", "饮食");
    }

    private boolean isFoodDescription(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        return description.contains("【食物】")
                || containsAny(description, "总估算热量", "食物名称", "千卡");
    }

    private String stripFoodJudgement(String description) {
        if (description == null || description.isBlank()) {
            return "未识别出清晰内容";
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : description.split("\\R")) {
            String normalized = line.trim();
            if (containsAny(normalized, "不是食物", "未出现食物", "不属于餐食", "不包含食物", "没有食物")) {
                continue;
            }
            if (!cleaned.isEmpty()) {
                cleaned.append('\n');
            }
            cleaned.append(line);
        }
        return cleaned.isEmpty() ? "未识别出清晰内容" : cleaned.toString().trim();
    }

    private void addImageToBuffer(String userId, byte[] bytes, String description) {
        List<ImageEntry> images = imageBuffers.computeIfAbsent(
                userId, ignored -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (images) {
            removeExpiredImages(images);
            if (images.size() >= MAX_IMAGES) {
                images.remove(0);
            }
            images.add(new ImageEntry(bytes, description));
        }
    }

    private List<ImageEntry> getValidImages(String userId) {
        List<ImageEntry> images = imageBuffers.get(userId);
        if (images == null) {
            return List.of();
        }
        synchronized (images) {
            removeExpiredImages(images);
            if (images.isEmpty()) {
                imageBuffers.remove(userId, images);
                return List.of();
            }
            return List.copyOf(images);
        }
    }

    private void removeExpiredImages(List<ImageEntry> images) {
        images.removeIf(image -> TimeUtil.isExpired(image.timestamp, IMAGE_BUFFER_TTL));
    }

    private String buildImageContext(List<ImageEntry> images, String text) {
        StringBuilder context = new StringBuilder("以下内容来自用户刚发送的健康相关图片。不得猜测看不清的信息：\n");
        for (int i = 0; i < images.size(); i++) {
            context.append("图片").append(i + 1).append("：")
                    .append(images.get(i).description).append('\n');
        }
        context.append("\n用户要求：").append(text);
        return context.toString();
    }

    private void clearImageBuffer(String userId) {
        imageBuffers.remove(userId);
    }

    private void sendReply(String userId, String result) {
        String reply = result == null || result.isBlank()
                ? "抱歉，我没理解你的意思，可以再说详细一点吗？"
                : result;
        try {
            requireClient().sendText(userId, reply);
            log.info("微信健康助手回复成功: outputChars={}", reply.length());
        } catch (Exception e) {
            log.error("微信健康助手回复失败", e);
        }
    }

    public boolean sendReminder(String userId, String content) {
        ILinkClient activeClient = getClient();
        if (activeClient == null || content == null || content.isBlank()) {
            return false;
        }
        try {
            activeClient.sendText(userId, "⏰ 健康提醒：" + content);
            log.info("健康提醒发送成功: contentChars={}", content.length());
            return true;
        } catch (Exception e) {
            log.error("健康提醒发送失败", e);
            return false;
        }
    }

    private ILinkClient requireClient() {
        ILinkClient activeClient = getClient();
        if (activeClient == null) {
            throw new IllegalStateException("WeChat client is not connected");
        }
        return activeClient;
    }

    private ILinkClient getClient() {
        String accountId = currentAccountId.get();
        if (accountId != null) {
            ILinkClient client = clientRegistry.get(accountId);
            if (client != null) {
                return client;
            }
        }
        return clientRegistry.all().values().stream().findFirst().orElse(null);
    }

    private MessageItem extractVoice(WeixinMessage message) {
        return message.getItem_list().stream()
                .filter(item -> item.getVoice_item() != null)
                .findFirst()
                .orElse(null);
    }

    private MessageItem extractImage(WeixinMessage message) {
        return message.getItem_list().stream()
                .filter(item -> item.getImage_item() != null)
                .findFirst()
                .orElse(null);
    }

    private MessageItem extractFile(WeixinMessage message) {
        return message.getItem_list().stream()
                .filter(item -> item.getFile_item() != null)
                .findFirst()
                .orElse(null);
    }

    private String extractText(WeixinMessage message) {
        return message.getItem_list().stream()
                .filter(item -> item.getText_item() != null)
                .map(item -> item.getText_item().getText())
                .findFirst()
                .orElse(null);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    public void cleanup() {
        botManager.stopAll();
        executor.shutdownNow();
        imageBuffers.clear();
        mealAnalysisLocks.clear();
        processedMessages.clear();
        log.info("微信健康助手已停止");
    }

    private static final class ImageEntry {
        private final byte[] bytes;
        private volatile String description;
        private final long timestamp;

        private ImageEntry(byte[] bytes, String description) {
            this.bytes = bytes;
            this.description = description;
            this.timestamp = TimeUtil.nowMillis();
        }
    }
}
