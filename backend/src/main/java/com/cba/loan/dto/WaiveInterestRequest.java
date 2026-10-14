package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Waives interest on an open loan.
 *
 * @param scope             ACCRUED: only interest accrued and unpaid. ALL (default): that, and
 *                          all interest not yet accrued (a modification of the loan's cash flows).
 * @param waiveDate         defaults to today; not in the future, not before the last accrual
 * @param approvalReference the credit committee or delegated authority approval, kept in the audit log
 */
public record WaiveInterestRequest(
        @NotBlank String reason,
        Scope scope,
        LocalDate waiveDate,
        @Size(max = 100) String approvalReference
) {
    public enum Scope { ACCRUED, ALL }

    public WaiveInterestRequest(String reason) {
        this(reason, null, null, null);
    }
}
