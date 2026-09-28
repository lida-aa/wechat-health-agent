package com.Myself.demo.service;

import java.util.List;

public final class AgentModelResult {

    private final String type;
    private final String content;
    private final List<FunctionCall> functionCalls;

    private AgentModelResult(String type, String content, List<FunctionCall> functionCalls) {
        this.type = type;
        this.content = content;
        this.functionCalls = List.copyOf(functionCalls);
    }

    public static AgentModelResult text(String content) {
        return new AgentModelResult("text", content, List.of());
    }

    public static AgentModelResult functionCall(List<FunctionCall> calls) {
        return new AgentModelResult("function_call", null, calls);
    }

    public boolean isText() {
        return "text".equals(type);
    }

    public boolean isFunctionCall() {
        return "function_call".equals(type);
    }

    public String getContent() {
        return content;
    }

    public List<FunctionCall> getFunctionCalls() {
        return functionCalls;
    }

    public record FunctionCall(String name, String args) {
    }
}
