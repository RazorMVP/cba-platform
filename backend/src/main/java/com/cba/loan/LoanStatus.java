package com.cba.loan;

public enum LoanStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    DISBURSED,
    ACTIVE,
    IN_ARREARS,             // Sub-state: loan is ACTIVE but has overdue installments
    CLOSED_OBLIGATIONS_MET,
    WRITTEN_OFF,
    FORECLOSED,
    REJECTED
}
