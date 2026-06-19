package com.getcloudledger.api.account.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmountRequest(
        @NotNull @DecimalMin(value = "0.01", message = "amount must be greater than zero") BigDecimal amount
) {
}
