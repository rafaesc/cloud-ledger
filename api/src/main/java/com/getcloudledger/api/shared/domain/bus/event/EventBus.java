package com.getcloudledger.api.shared.domain.bus.event;

import java.util.List;

public interface EventBus {
    void publish(final String aggregateType, final List<? extends BaseEvent> events);
}
