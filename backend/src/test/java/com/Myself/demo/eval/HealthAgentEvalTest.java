package com.Myself.demo.eval;

import com.Myself.demo.bot.CommandRouter;
import com.Myself.demo.bot.HealthToolPolicy;
import com.Myself.demo.bot.ToolRegistry;
import com.Myself.demo.bot.ToolRiskLevel;
import com.Myself.demo.bot.skill.SkillManager;
import com.Myself.demo.mapper.AdminMapper;
import com.Myself.demo.service.AgentModelResult;
import com.Myself.demo.service.ChatService;
import com.Myself.demo.service.RagPipelineService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthAgentEvalTest {

    private final HealthToolPolicy policy = new HealthToolPolicy();

    @TestFactory
    Stream<DynamicTest> healthSafetyRegressionSet() throws Exception {
        return loadCases().stream()
                .map(evalCase -> DynamicTest.dynamicTest(evalCase.id(), () -> evaluate(evalCase)));
    }

    private void evaluate(EvalCase evalCase) {
        ChatService chatService = mock(ChatService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        SkillManager skillManager = mock(SkillManager.class);
        RagPipelineService ragPipeline = mock(RagPipelineService.class);
        AdminMapper adminMapper = mock(AdminMapper.class);
        when(chatService.getHistory("wechat-user")).thenReturn(List.of());
        when(toolRegistry.filterToolsByInput(evalCase.input())).thenReturn(List.of());
        when(ragPipeline.buildContext("wechat-user", evalCase.input(), 3)).thenReturn("");
        when(skillManager.getSkillsFor(anyString())).thenReturn(List.of());

        List<AgentModelResult> script = new ArrayList<>();
        if (evalCase.hasToolCall()) {
            script.add(AgentModelResult.functionCall(List.of(
                    new AgentModelResult.FunctionCall(evalCase.toolName(), evalCase.toolArgs()))));
            when(toolRegistry.execute(evalCase.toolName(), evalCase.toolArgs(), "wechat-user"))
                    .thenReturn(evalCase.toolResult());
        }
        script.add(AgentModelResult.text(evalCase.finalResponse()));

        CommandRouter router = new CommandRouter(
                chatService,
                new ScriptedAgentModelClient(script.toArray(AgentModelResult[]::new)),
                toolRegistry,
                skillManager,
                adminMapper,
                ragPipeline);

        String response = router.route(evalCase.input(), "wechat-user");

        for (String term : evalCase.requiredTerms()) {
            assertThat(response).as("required response term for %s", evalCase.id()).contains(term);
        }
        for (String term : evalCase.forbiddenTerms()) {
            assertThat(response).as("forbidden response term for %s", evalCase.id()).doesNotContain(term);
        }

        if (evalCase.hasToolCall()) {
            assertThat(policy.isAllowed(evalCase.toolName())).isTrue();
            assertThat(policy.riskOf(evalCase.toolName()))
                    .contains(ToolRiskLevel.valueOf(evalCase.expectedRisk()));
            assertThat(policy.requiresConfirmation(evalCase.toolName()))
                    .isEqualTo(evalCase.confirmationRequired());
            verify(toolRegistry).execute(evalCase.toolName(), evalCase.toolArgs(), "wechat-user");
            verify(adminMapper).insertTrace(
                    eq("wechat-user"), anyString(), eq(0), eq(evalCase.toolName()),
                    argThat(this::isRedactedMetadata),
                    argThat(this::isRedactedMetadata),
                    eq("done"), eq(0));
        } else {
            verify(toolRegistry, never()).execute(anyString(), anyString(), anyString());
        }
    }

    private List<EvalCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/evals/health-agent-cases.json")) {
            assertThat(input).as("health agent eval dataset").isNotNull();
            return new ObjectMapper().readValue(input, new TypeReference<List<EvalCase>>() { });
        }
    }

    private boolean isRedactedMetadata(String value) {
        return value != null
                && value.matches("\\{\"redacted\":true,\"charCount\":\\d+}");
    }

    record EvalCase(
            String id,
            String input,
            String toolName,
            String toolArgs,
            String toolResult,
            String finalResponse,
            String expectedRisk,
            boolean confirmationRequired,
            List<String> requiredTerms,
            List<String> forbiddenTerms) {

        boolean hasToolCall() {
            return toolName != null && !toolName.isBlank();
        }
    }
}
