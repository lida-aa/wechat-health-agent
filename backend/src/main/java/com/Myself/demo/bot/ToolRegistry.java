package com.Myself.demo.bot;

import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final HealthToolPolicy toolPolicy;
    private final Map<String, ToolEntry> tools = new HashMap<>();
    private volatile List<ToolFunction> cachedTools;

    public ToolRegistry(ApplicationContext applicationContext, HealthToolPolicy toolPolicy) {
        this.applicationContext = applicationContext;
        this.toolPolicy = toolPolicy;
    }

    @PostConstruct
    public void init() {
        tools.clear();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) continue;
            methodLoop:
            for (Method method : beanType.getDeclaredMethods()) {
                Tool toolAnno = method.getAnnotation(Tool.class);
                if (toolAnno == null) continue;

                String toolName = toolAnno.name();
                if (toolName == null || toolName.isEmpty()) {
                    toolName = method.getName();
                }
                if (!toolPolicy.isAllowed(toolName)) {
                    log.debug("健康 Agent 未授权工具，跳过注册: {}", toolName);
                    continue;
                }
                if (toolPolicy.requiresConfirmation(toolName)
                        && !method.isAnnotationPresent(CreatesHealthActionDraft.class)) {
                    log.error("敏感写工具未声明草稿边界，拒绝注册: {}", toolName);
                    continue;
                }
                String description = toolAnno.value().length > 0
                        ? String.join(" ", toolAnno.value())
                        : method.getName();

                Parameter[] params = method.getParameters();
                List<ParamDef> paramDefs = new ArrayList<>();

                for (Parameter param : params) {
                    P pAnno = param.getAnnotation(P.class);
                    boolean currentUser = param.isAnnotationPresent(CurrentUserId.class);
                    if (pAnno == null) {
                        if (currentUser) {
                            log.error("@CurrentUserId 参数缺少 @P，拒绝注册工具: {}", toolName);
                            continue methodLoop;
                        }
                        continue;
                    }
                    String paramName = param.getName();
                    String paramDesc = pAnno.value();
                    Class<?> paramType = param.getType();
                    boolean looksLikeUserId = "userId".equalsIgnoreCase(paramName)
                            || paramDesc.contains("用户ID")
                            || paramDesc.toLowerCase().contains("userid");
                    if (looksLikeUserId && !currentUser) {
                        log.error("用户身份参数未标记 @CurrentUserId，拒绝注册工具: {}", toolName);
                        continue methodLoop;
                    }
                    if (currentUser && paramType != String.class) {
                        log.error("@CurrentUserId 只支持 String 参数，拒绝注册工具: {}", toolName);
                        continue methodLoop;
                    }

                    paramDefs.add(new ParamDef(paramName, paramDesc, paramType, currentUser));
                }

                if (tools.containsKey(toolName)) {
                    log.warn("同名工具已存在，跳过: {}", toolName);
                    continue;
                }

                tools.put(toolName, new ToolEntry(toolName, description, beanName, method, paramDefs));
                log.info("注册工具: {} ({})", toolName, beanType.getSimpleName());
            }
        }
        cachedTools = null;
        log.info("健康 Agent 工具注册完成: {}", tools.keySet());
    }

    public List<ToolFunction> buildTools() {
        if (cachedTools != null) return cachedTools;
        List<ToolFunction> result = new ArrayList<>();
        for (ToolEntry entry : tools.values()) {
            if (entry.toolFunction != null) {
                result.add(entry.toolFunction);
            } else if (entry.paramDefs != null) {
                ToolFunction tf = ToolFunction.builder()
                    .function(FunctionDefinition.builder()
                            .name(entry.name)
                            .description(entry.description)
                            .parameters(buildParams(entry))
                            .build())
                    .build();
                result.add(tf);
            }
        }
        cachedTools = result;
        return cachedTools;
    }

    public List<ToolFunction> filterToolsByInput(String userInput) {
        return buildTools();
    }

    private JsonObject buildParams(ToolEntry entry) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();

        for (ParamDef pd : entry.paramDefs) {
            if (pd.systemParam) continue;

            JsonObject prop = new JsonObject();
            if (pd.type == String.class) {
                prop.addProperty("type", "string");
            } else if (pd.type == int.class || pd.type == Integer.class) {
                prop.addProperty("type", "integer");
            } else if (pd.type == double.class || pd.type == Double.class) {
                prop.addProperty("type", "number");
            } else if (pd.type == boolean.class || pd.type == Boolean.class) {
                prop.addProperty("type", "boolean");
            } else {
                prop.addProperty("type", "string");
            }
            prop.addProperty("description", pd.description);
            properties.add(pd.name, prop);
            required.add(new JsonPrimitive(pd.name));
        }

        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    public void registerExternal(String toolName, ToolFunction toolFunction,
                                   TriFunction<String, String, String, String> executor) {
        if (!toolPolicy.isAllowed(toolName) || toolPolicy.requiresConfirmation(toolName)) {
            log.info("健康 Agent 拒绝注册外部工具: {}", toolName);
            return;
        }
        tools.put(toolName, new ToolEntry(toolName, toolFunction.getFunction().getDescription(), executor, toolFunction));
        cachedTools = null;
        log.info("外部工具已注册: {}", toolName);
    }

    public String execute(String toolName, String llmArgs, String userId) {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) return null;
        if (entry.requiresUserContext() && (userId == null || userId.isBlank())) {
            log.warn("工具缺少当前用户身份，拒绝执行: {}", toolName);
            return "用户身份上下文缺失，拒绝执行工具";
        }

        // 外部工具（MCP等）
        if (entry.executor != null) {
            try {
                return entry.executor.apply(toolName, llmArgs, userId);
            } catch (Exception e) {
                log.error("外部工具执行失败: {}", toolName, e);
                return "工具执行失败";
            }
        }

        // @Tool 反射执行
        try {
            List<Object> callArgs = new ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode argsNode = parseArgs(mapper, llmArgs);

            for (ParamDef pd : entry.paramDefs) {
                if (pd.systemParam) {
                    callArgs.add(userId);
                } else {
                    com.fasterxml.jackson.databind.JsonNode node = argsNode.get(pd.name);
                    if (pd.type == String.class) {
                        callArgs.add(node != null && !node.isNull() ? node.asText() : "");
                    } else if (pd.type == int.class || pd.type == Integer.class) {
                        callArgs.add(node != null && !node.isNull() ? extractNumber(node.asText(), 0) : 0);
                    } else if (pd.type == double.class || pd.type == Double.class) {
                        callArgs.add(node != null && !node.isNull() ? extractDouble(node.asText(), 0.0) : 0.0);
                    } else if (pd.type == boolean.class || pd.type == Boolean.class) {
                        callArgs.add(node != null && !node.isNull() ? node.asBoolean() : false);
                    } else {
                        callArgs.add(node != null && !node.isNull() ? node.asText() : "");
                    }
                }
            }

            Object bean = applicationContext.getBean(entry.beanName);
            return (String) entry.method.invoke(bean, callArgs.toArray());
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "工具执行失败";
        }
    }

    /** Scheduled jobs may execute read-only tools only. */
    public String executeScheduled(String toolName, String args, String userId) {
        if (!toolPolicy.canRunAutomatically(toolName)) {
            log.warn("定时任务拒绝自动执行非只读工具: {}", toolName);
            return "该工具涉及数据修改，不能由定时任务自动执行";
        }
        return execute(toolName, args, userId);
    }

    /** LLM 参数容错解析：数值带中文单位（如 "remaining": 30片）、单引号、尾逗号等常见非严格 JSON。 */
    private com.fasterxml.jackson.databind.JsonNode parseArgs(com.fasterxml.jackson.databind.ObjectMapper mapper, String llmArgs) throws Exception {
        String raw = llmArgs == null ? "{}" : llmArgs;
        try {
            return mapper.readTree(raw);
        } catch (Exception ignored) {
            String fixed = raw
                    .replaceAll("(:\\s*\\d+)[\\u4e00-\\u9fa5A-Za-z]+(?=\\s*[,}])", "$1")
                    .replace('\'', '"')
                    .replaceAll(",\\s*([}\\]])", "$1")
                    .replaceAll("(?<=[{,])\\s*([\\u4e00-\\u9fa5A-Za-z_]+)\\s*:", "\"$1\":");
            return mapper.readTree(fixed);
        }
    }

    private int extractNumber(String text, int defaultValue) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-+]?\\d+").matcher(text);
            return m.find() ? Integer.parseInt(m.group()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double extractDouble(String text, double defaultValue) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(text);
            return m.find() ? Double.parseDouble(m.group()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    private static class ToolEntry {
        final String name;
        final String description;
        final String beanName;
        final Method method;
        final List<ParamDef> paramDefs;
        final TriFunction<String, String, String, String> executor;
        final ToolFunction toolFunction;

        ToolEntry(String name, String description, String beanName, Method method, List<ParamDef> paramDefs) {
            this.name = name;
            this.description = description;
            this.beanName = beanName;
            this.method = method;
            this.paramDefs = paramDefs;
            this.executor = null;
            this.toolFunction = null;
        }

        ToolEntry(String name, String description, TriFunction<String, String, String, String> executor, ToolFunction toolFunction) {
            this.name = name;
            this.description = description;
            this.beanName = null;
            this.method = null;
            this.paramDefs = null;
            this.executor = executor;
            this.toolFunction = toolFunction;
        }

        boolean requiresUserContext() {
            return paramDefs != null && paramDefs.stream().anyMatch(param -> param.systemParam);
        }
    }

    private static class ParamDef {
        final String name;
        final String description;
        final Class<?> type;
        final boolean systemParam;

        ParamDef(String name, String description, Class<?> type, boolean systemParam) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.systemParam = systemParam;
        }
    }
}
