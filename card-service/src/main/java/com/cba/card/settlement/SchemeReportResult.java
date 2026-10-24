package com.cba.card.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The outcome of recording a scheme settlement report, or the expected net for a preview. A refusal is
 * a result, not an error.
 *
 * @param recorded    true when the report is recorded and the cash leg posted (now, or by an earlier identical report)
 * @param repeat      true when an identical report was already recorded (nothing posted again)
 * @param reason      why not: AMOUNT_MISMATCH, ALREADY_RECORDED, REPORT_IN_PROGRESS, DATE_NOT_CLOSED,
 *                    BATCH_NOT_CLOSED, UNKNOWN_CURRENCY, INVALID_AMOUNT, BACKEND_UNAVAILABLE, or the backend's refusal
 * @param expectedNet what the bank owes the scheme for the date (major units, signed)
 * @param difference  reported − expected
 */
public record SchemeReportResult(boolean recorded, boolean repeat, String reason, String scheme,
                                 LocalDate settlementDate, String currency, BigDecimal expectedNet,
                                 BigDecimal reportedNet, BigDecimal difference, int itemCount, int refundCount,
                                 String backendReference, String journalTransactionId) {}
