package com.cba.customer.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for Mifos-style command requests on a customer:
 *   POST /api/v1/customers/{id}?command=reject|withdraw|reactivate|undoRejection|undoWithdrawal
 *   POST /api/v1/customers/{id}?command=assignStaff|unassignStaff
 *   POST /api/v1/customers/{id}?command=proposeTransfer|acceptTransfer|rejectTransfer|withdrawTransfer
 *
 * All fields are optional — each command uses the subset it needs.
 */
public record CustomerCommandRequest(

        /** Used by: reject, withdraw, close */
        String reason,

        /** Used by: assignStaff */
        UUID staffId,

        /** Used by: proposeTransfer, acceptTransfer */
        UUID destinationOfficeId,

        /** Used by: proposeTransfer */
        LocalDate transferDate,

        /** Used by: proposeTransfer */
        String transferNote

) {
    public CustomerCommandRequest() {
        this(null, null, null, null, null);
    }
}
