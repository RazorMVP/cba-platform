package com.cba.bulkimport;

import java.util.List;

public record BulkImportResult(
        String jobId,
        String entityType,
        int totalRows,
        int successCount,
        int failureCount,
        String status,
        List<RowError> errors
) {
    public record RowError(int row, String field, String message) {}

    public static BulkImportResult of(BulkImportJob job, List<RowError> errors) {
        return new BulkImportResult(
                job.getId().toString(),
                job.getEntityType(),
                job.getTotalRows(),
                job.getSuccessCount(),
                job.getFailureCount(),
                job.getStatus(),
                errors
        );
    }
}
