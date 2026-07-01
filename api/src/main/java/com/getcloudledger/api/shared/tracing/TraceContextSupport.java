package com.getcloudledger.api.shared.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the active OpenTelemetry trace context into a plain {@code Map} carrier so it can be
 * persisted (e.g. onto an outbox row) and later re-injected by a downstream relay.
 *
 * <p>The trace machinery is supplied at runtime by the ADOT Java agent. When the agent is NOT
 * attached (local dev, tests), {@link GlobalOpenTelemetry#getPropagators()} returns no-op
 * propagators, so {@link #currentCarrier()} simply returns an empty map — no crash, no context.
 */
public final class TraceContextSupport {

    private TraceContextSupport() {
    }

    /**
     * Injects the current trace context into a fresh W3C carrier. The map will typically contain
     * {@code traceparent} (and {@code tracestate} when present); it is empty when there is no
     * active recording span.
     */
    public static Map<String, String> currentCarrier() {
        Map<String, String> carrier = new HashMap<>();
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), carrier, (c, key, value) -> {
                    if (c != null) {
                        c.put(key, value);
                    }
                });
        return carrier;
    }
}
