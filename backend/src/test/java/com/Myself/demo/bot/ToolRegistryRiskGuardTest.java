package com.Myself.demo.bot;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryRiskGuardTest {

    @Test
    void rejectsSensitiveToolThatDoesNotDeclareDraftBoundary() {
        ToolRegistry registry = registryFor(new UnsafeMedicationTool());

        registry.init();

        assertThat(registry.execute("add_medication", "{}", "user-a")).isNull();
    }

    @Test
    void sensitiveDraftToolCanRunInteractivelyButNeverOnSchedule() {
        ToolRegistry registry = registryFor(new SafeMedicationDraftTool());
        registry.init();

        assertThat(registry.execute("add_medication", "{}", "user-a"))
                .isEqualTo("draft only");
        assertThat(registry.executeScheduled("add_medication", "{}", "user-a"))
                .contains("不能由定时任务自动执行");
    }

    @Test
    void readOnlyToolCanRunOnSchedule() {
        ToolRegistry registry = registryFor(new ReadOnlyHealthTipTool());
        registry.init();

        assertThat(registry.executeScheduled("health_tip", "{}", "user-a"))
                .isEqualTo("safe tip");
    }

    @Test
    void currentUserComesFromChannelContextAndIsHiddenFromModelSchema() {
        ToolRegistry registry = registryFor(new UserScopedHealthTool());
        registry.init();

        assertThat(registry.execute("health_overview", "{\"userId\":\"victim\"}", "actual-user"))
                .isEqualTo("actual-user");
        assertThat(registry.buildTools()).hasSize(1);
        assertThat(registry.buildTools().get(0).getFunction().getParameters().toString())
                .doesNotContain("userId", "用户ID");
        assertThat(registry.execute("health_overview", "{}", ""))
                .contains("用户身份上下文缺失");
    }

    @Test
    void rejectsUserScopedToolMissingCurrentUserAnnotation() {
        ToolRegistry registry = registryFor(new UnscopedHealthTool());

        registry.init();

        assertThat(registry.execute("health_overview", "{}", "actual-user")).isNull();
        assertThat(registry.buildTools()).isEmpty();
    }

    private ToolRegistry registryFor(Object bean) {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"testTool"});
        doReturn(bean.getClass()).when(context).getType("testTool");
        when(context.getBean("testTool")).thenReturn(bean);
        return new ToolRegistry(context, new HealthToolPolicy());
    }

    static class UnsafeMedicationTool {
        @Tool(name = "add_medication", value = "unsafe direct write")
        public String write() {
            return "wrote directly";
        }
    }

    static class SafeMedicationDraftTool {
        @CreatesHealthActionDraft
        @Tool(name = "add_medication", value = "creates a draft")
        public String draft() {
            return "draft only";
        }
    }

    static class ReadOnlyHealthTipTool {
        @Tool(name = "health_tip", value = "read-only tip")
        public String tip() {
            return "safe tip";
        }
    }

    static class UserScopedHealthTool {
        @Tool(name = "health_overview", value = "user health overview")
        public String read(@CurrentUserId @P("用户ID") String userId) {
            return userId;
        }
    }

    static class UnscopedHealthTool {
        @Tool(name = "health_overview", value = "unsafe user health overview")
        public String read(@P("用户ID") String userId) {
            return userId;
        }
    }
}
