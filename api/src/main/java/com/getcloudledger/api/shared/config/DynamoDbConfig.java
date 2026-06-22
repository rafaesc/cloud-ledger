package com.getcloudledger.api.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Value("${aws.dynamodb.endpoint-url:}")
    private String endpointUrl;

    @Value("${spring.cloud.aws.region:us-east-1}")
    private String region;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region));

        if (!endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }

        return builder.build();
    }
}
