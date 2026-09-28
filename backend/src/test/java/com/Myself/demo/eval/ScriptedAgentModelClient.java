package com.Myself.demo.eval;

import com.Myself.demo.service.AgentModelClient;
import com.Myself.demo.service.AgentModelResult;
import com.alibaba.dashscope.tools.ToolBase;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

final class ScriptedAgentModelClient implements AgentModelClient {

    private final Deque<AgentModelResult> results;

    ScriptedAgentModelClient(AgentModelResult... results) {
        this.results = new ArrayDeque<>(Arrays.asList(results));
    }

    @Override
    public JsonArray buildBaseMessages(String userId, List<Map<String, String>> history) {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "health-agent-eval");
        messages.add(system);
        return messages;
    }

    @Override
    public AgentModelResult chatRaw(JsonArray messages, List<? extends ToolBase> tools) {
        if (results.isEmpty()) {
            throw new IllegalStateException("scripted model has no result left");
        }
        return results.removeFirst();
    }

    @Override
    public JsonObject buildAssistantWithToolCalls(List<AgentModelResult.FunctionCall> calls) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", "scripted tool call");
        return message;
    }

    @Override
    public JsonObject buildToolResult(
            int index, AgentModelResult.FunctionCall call, String result) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "tool");
        message.addProperty("name", call.name());
        message.addProperty("content", result);
        return message;
    }

    @Override
    public String chatStream(JsonArray messages, List<ToolBase> tools) {
        AgentModelResult result = chatRaw(messages, tools);
        return result.getContent();
    }

    @Override
    public int countTokens(JsonArray messages) {
        return 0;
    }
}
