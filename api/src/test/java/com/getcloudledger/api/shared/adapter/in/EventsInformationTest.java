package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.domain.bus.event.EventsInformation;
import com.getcloudledger.api.testing.events.FirstTestEvent;
import com.getcloudledger.api.testing.events.IntegrationTestEvent;
import com.getcloudledger.api.testing.events.SecondTestEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsInformationTest {

    @Test
    void search_returns_correct_classes_for_multiple_events() {
        EventsInformation info = new EventsInformation();

        Class<?> first = info.search(FirstTestEvent.eventName());
        Class<?> second = info.search(SecondTestEvent.eventName());
        Class<?> integration = info.search(IntegrationTestEvent.eventName());

        assertEquals(FirstTestEvent.class, first);
        assertEquals(SecondTestEvent.class, second);
        assertEquals(IntegrationTestEvent.class, integration);
    }

    @Test
    void search_throws_for_unknown_event() {
        EventsInformation info = new EventsInformation();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> info.search("non.existent.event")
        );

        assertTrue(ex.getMessage().contains("No event with name"));
    }
}
