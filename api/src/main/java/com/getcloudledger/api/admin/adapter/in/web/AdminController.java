package com.getcloudledger.api.admin.adapter.in.web;

import com.getcloudledger.api.admin.application.getrebuildjob.GetRebuildJobQuery;
import com.getcloudledger.api.admin.application.getrebuildjob.RebuildJobResponse;
import com.getcloudledger.api.admin.application.rebuild.RebuildProjectionsCommand;
import com.getcloudledger.api.shared.domain.DomainError;
import com.getcloudledger.api.shared.domain.bus.command.CommandBus;
import com.getcloudledger.api.shared.domain.bus.query.QueryBus;
import com.getcloudledger.api.shared.spring.ApiController;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.UUID;

/**
 * Operational admin endpoints, guarded by the {@code api/admin} Cognito scope only (no per-account
 * ownership). Kicks off async DynamoDB projection rebuilds and reports their progress.
 *
 * <p>Note: POST goes through {@code IdempotencyFilter} like every other {@code /v1/} mutation, so an
 * {@code Idempotency-Key} header is required — a natural guard against double-submitting a rebuild.
 */
@RestController
@RequestMapping("/v1/admin/projections")
public class AdminController extends ApiController {

    public AdminController(QueryBus queryBus, CommandBus commandBus) {
        super(queryBus, commandBus);
    }

    /**
     * Full rebuild (all events) when {@code account_id} is absent, or a single-aggregate replay when
     * present. The job id is generated here (server-side, not client-supplied) so it can be dispatched
     * inside the command and immediately read back via {@code GetRebuildJobQuery} for the 202 body.
     */
    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@adminSecurity.hasAdminScope(authentication)")
    public RebuildJobResponse rebuild(@RequestParam(name = "account_id", required = false) UUID accountId) {
        var jobId = UUID.randomUUID();
        dispatch(new RebuildProjectionsCommand(jobId, accountId));
        return ask(new GetRebuildJobQuery(jobId));
    }

    @GetMapping("/rebuild/{jobId}")
    @PreAuthorize("@adminSecurity.hasAdminScope(authentication)")
    public RebuildJobResponse status(@PathVariable UUID jobId) {
        return ask(new GetRebuildJobQuery(jobId));
    }

    @Override
    public HashMap<Class<? extends DomainError>, HttpStatus> errorMapping() {
        return new HashMap<>();
    }
}
