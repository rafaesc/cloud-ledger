package com.getcloudledger.api.admin.application.getrebuildjob;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class GetRebuildJobQuery implements Query {
    private final UUID jobId;
}
