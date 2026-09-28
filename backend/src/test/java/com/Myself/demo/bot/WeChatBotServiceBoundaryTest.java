package com.Myself.demo.bot;

import com.Myself.demo.service.ImageService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WeChatBotServiceBoundaryTest {

    @Test
    void channelOnlyDependsOnHealthAgentInfrastructure() {
        Set<String> dependencyTypes = Arrays.stream(WeChatBotService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .collect(Collectors.toSet());

        assertThat(dependencyTypes)
                .contains("CommandRouter", "ImageService", "BotManager",
                        "WeChatClientRegistry", "HealthActionCoordinator")
                .doesNotContain("LlmService", "RagService", "VoiceService",
                        "VoicePreferenceService", "JdbcTemplate", "AdminMapper");
    }

    @Test
    void legacyGeneralPurposeActionsAreNotHandledByChannel() {
        Set<String> methodNames = Arrays.stream(WeChatBotService.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(methodNames)
                .doesNotContain("doImageGen", "doImageTransform", "doQrCode", "doDecodeQr",
                        "doFileExport", "doSaveAsFile", "doFileToolAction", "handleFileMessage",
                        "handleKbListCommand", "handleKbDeleteCommand", "handleKbClearCommand",
                        "saveUserImage", "saveUserFile");
    }

    @Test
    void imageServiceOnlyExposesHealthImageRecognition() {
        Set<String> publicMethodNames = Arrays.stream(ImageService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(publicMethodNames)
                .containsExactly("recognizeImage")
                .doesNotContain("generateImage", "generateAndSave", "editImageAndSave");
    }
}
