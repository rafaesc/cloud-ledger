package com.getcloudledger.api.admin.application.getrebuildjob;

import com.getcloudledger.api.admin.application.rebuild.RebuildJobStore;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetRebuildJobQueryHandler implements QueryHandler<GetRebuildJobQuery, RebuildJobResponse> {

    private final RebuildJobStore jobStore;

    @Override
    public RebuildJobResponse handle(GetRebuildJobQuery query) {
        return jobStore.find(query.getJobId())
                .map(RebuildJobResponse::from)
                .orElseThrow(() -> new RebuildJobNotFoundException(query.getJobId()));
    }
}
