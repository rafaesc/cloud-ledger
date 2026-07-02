package com.getcloudledger.api.admin.application.getrebuildjob;

import java.util.UUID;

public class RebuildJobNotFoundException extends RuntimeException {

    public RebuildJobNotFoundException(UUID jobId) {
        super("Rebuild job not found: " + jobId);
    }
}
