package com.getcloudledger.api.shared.adapter.out.sqs;

import com.getcloudledger.api.shared.domain.bus.event.BaseEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEventJsonSerializer;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

@Service
@Qualifier("sqs")
@Slf4j
@RequiredArgsConstructor
public class SqsEventBus implements EventBus {

    private final SqsClient sqsClient;

    @Value("${spring.cloud.aws.sqs.queue-url}")
    private String queueUrl;

    @Override
    public void publish(String aggregateType, List<? extends BaseEvent> events) {
        for (var event : events) {
            if (!(event instanceof DomainEvent domainEvent)) {
                log.warn("Skipping non-DomainEvent in SQS publish: {}", event.getClass().getSimpleName());
                continue;
            }
            String body = DomainEventJsonSerializer.serialize(domainEvent);
            log.info("Sending event to SQS: type={} eventId={}",
                    domainEvent.getClass().getSimpleName(), domainEvent.getEventId());
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            log.info("Event sent to SQS successfully: type={} eventId={}",
                    domainEvent.getClass().getSimpleName(), domainEvent.getEventId());
        }
    }
}
