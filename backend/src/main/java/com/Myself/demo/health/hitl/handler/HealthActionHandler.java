package com.Myself.demo.health.hitl.handler;

import com.Myself.demo.health.hitl.PendingHealthAction;

import java.util.Set;

/** Executes and renders one or more related health action types. */
public interface HealthActionHandler {

    Set<PendingHealthAction.Type> supportedTypes();

    String execute(PendingHealthAction action);

    String render(PendingHealthAction action);

    String label(PendingHealthAction action);
}
