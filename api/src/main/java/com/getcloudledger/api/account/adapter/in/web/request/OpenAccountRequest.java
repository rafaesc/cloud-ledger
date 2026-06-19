package com.getcloudledger.api.account.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenAccountRequest(
        @NotNull UUID accountId,
        @NotBlank String currency
) {
}
