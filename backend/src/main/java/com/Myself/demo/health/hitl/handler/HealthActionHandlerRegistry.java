package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.PendingHealthAction;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class HealthActionHandlerRegistry {

    private final Map<PendingHealthAction.Type, HealthActionHandler> handlers;

    public HealthActionHandlerRegistry(List<HealthActionHandler> candidates) {
        EnumMap<PendingHealthAction.Type, HealthActionHandler> registered =
                new EnumMap<>(PendingHealthAction.Type.class);
        for (HealthActionHandler candidate : candidates) {
            for (PendingHealthAction.Type type : candidate.supportedTypes()) {
                HealthActionHandler previous = registered.putIfAbsent(type, candidate);
                if (previous != null) {
                    throw new IllegalStateException("健康动作存在重复处理器: " + type);
                }
            }
        }
        for (PendingHealthAction.Type type : PendingHealthAction.Type.values()) {
            if (!registered.containsKey(type)) {
                throw new IllegalStateException("健康动作缺少处理器: " + type);
            }
        }
        handlers = Map.copyOf(registered);
    }

    public String execute(PendingHealthAction action) {
        return handlerFor(action.type()).execute(action);
    }

    public String render(PendingHealthAction action) {
        return handlerFor(action.type()).render(action);
    }

    public String label(PendingHealthAction action) {
        return handlerFor(action.type()).label(action);
    }

    private HealthActionHandler handlerFor(PendingHealthAction.Type type) {
        HealthActionHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("健康动作缺少处理器: " + type);
        }
        return handler;
    }
}
