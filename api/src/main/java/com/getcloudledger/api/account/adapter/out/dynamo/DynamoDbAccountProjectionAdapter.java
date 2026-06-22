package com.getcloudledger.api.account.adapter.out.dynamo;

import com.getcloudledger.api.account.domain.exception.AccountNotFoundException;
import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamoDbAccountProjectionAdapter implements AccountProjectionPort {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-name:cloudledger-projections}")
    private String tableName;

    @Override
    public AccountStateView findAccountState(UUID accountId) {
        var response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ACCOUNT#" + accountId),
                        "SK", AttributeValue.fromS("STATE")
                ))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        var item = response.item();
        return new AccountStateView(
                accountId.toString(),
                item.get("owner_id").s(),
                item.get("status").s(),
                item.get("currency").s(),
                Long.parseLong(item.get("version").n()),
                item.get("opened_at").s(),
                item.containsKey("frozen_at") ? item.get("frozen_at").s() : null,
                item.containsKey("closed_at") ? item.get("closed_at").s() : null
        );
    }

    @Override
    public AccountBalanceView findAccountBalance(UUID accountId) {
        var response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ACCOUNT#" + accountId),
                        "SK", AttributeValue.fromS("BALANCE")
                ))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        var item = response.item();
        return new AccountBalanceView(
                accountId.toString(),
                Long.parseLong(item.get("balance_cents").n()),
                item.get("currency").s(),
                Long.parseLong(item.get("version").n()),
                item.get("updated_at").s()
        );
    }

    @Override
    public TransactionPage findTransactions(UUID accountId, int limit, String startSK, boolean ascending) {
        var pk = AttributeValue.fromS("ACCOUNT#" + accountId);

        var expressionValues = new HashMap<String, AttributeValue>();
        expressionValues.put(":pk", pk);
        expressionValues.put(":prefix", AttributeValue.fromS("TXNS#"));

        var builder = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(expressionValues)
                .scanIndexForward(ascending)
                .limit(limit);

        if (startSK != null) {
            builder.exclusiveStartKey(Map.of(
                    "PK", pk,
                    "SK", AttributeValue.fromS(startSK)
            ));
        }

        var response = dynamoDbClient.query(builder.build());

        List<TransactionView> items = new ArrayList<>();
        for (var item : response.items()) {
            items.add(new TransactionView(
                    Long.parseLong(item.get("sequence_number").n()),
                    item.get("event_type").s(),
                    item.get("direction").s(),
                    Long.parseLong(item.get("amount_cents").n()),
                    item.containsKey("counterpart_account_id") ? item.get("counterpart_account_id").s() : null,
                    item.containsKey("transfer_id") ? item.get("transfer_id").s() : null,
                    item.get("event_id").s(),
                    item.get("event_at").s()
            ));
        }

        String nextCursor = null;
        if (response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()) {
            String lastSK = response.lastEvaluatedKey().get("SK").s();
            nextCursor = Base64.getEncoder().encodeToString(lastSK.getBytes(StandardCharsets.UTF_8));
        }

        return new TransactionPage(items, nextCursor);
    }

    @Override
    public List<AccountSummaryView> listByOwner(String ownerId) {
        var response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :gsi1pk")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS("OWNER#" + ownerId)
                ))
                .build());

        List<AccountSummaryView> items = new ArrayList<>();
        for (var item : response.items()) {
            String accountId = item.get("GSI1SK").s().substring("ACCOUNT#".length());
            items.add(new AccountSummaryView(
                    accountId,
                    item.get("status").s(),
                    item.get("currency").s(),
                    item.get("opened_at").s()
            ));
        }
        return items;
    }
}
