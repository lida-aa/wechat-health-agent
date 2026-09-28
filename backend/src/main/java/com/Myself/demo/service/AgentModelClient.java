package com.Myself.demo.service;

import com.alibaba.dashscope.tools.ToolBase;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Model boundary used by the agent loop and deterministic eval clients. */
public interface AgentModelClient {

    JsonArray buildBaseMessages(String userId, List<Map<String, String>> history);

    AgentModelResult chatRaw(JsonArray messages, List<? extends ToolBase> tools);

    JsonObject buildAssistantWithToolCalls(List<AgentModelResult.FunctionCall> calls);

    JsonObject buildToolResult(
            int index, AgentModelResult.FunctionCall call, String result);

    String chatStream(JsonArray messages, List<ToolBase> tools);

    int countTokens(JsonArray messages);
}
