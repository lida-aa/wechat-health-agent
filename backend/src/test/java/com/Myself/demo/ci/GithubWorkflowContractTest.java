package com.Myself.demo.ci;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GithubWorkflowContractTest {

    @Test
    void workflowIsValidYamlAndKeepsRequiredSafetySteps() throws IOException {
        Path workflowPath = locateWorkflow();
        assertThat(workflowPath).exists();
        String source = Files.readString(workflowPath);
        Object parsed = new Yaml().load(source);

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> workflow = (Map<?, ?>) parsed;
        assertThat(workflow.containsKey("name")).isTrue();
        assertThat(workflow.containsKey("jobs")).isTrue();
        assertThat(source)
                .contains("contents: read")
                .contains("actions/checkout@v7")
                .contains("actions/setup-java@v6")
                .contains("actions/setup-node@v7")
                .contains("java-version: '21'")
                .contains("backend/scripts/ci/check-sensitive-files.sh")
                .contains("GithubWorkflowContractTest")
                .contains("HealthAgentEvalTest")
                .contains("npm run check")
                .contains("npm run build");
    }

    private static Path locateWorkflow() {
        Path repositoryWorkflow = Path.of(".github", "workflows", "ci.yml");
        if (Files.exists(repositoryWorkflow)) {
            return repositoryWorkflow;
        }
        return Path.of("..", ".github", "workflows", "ci.yml");
    }
}
