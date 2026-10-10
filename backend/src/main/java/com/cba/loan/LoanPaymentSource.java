package com.cba.loan;

import com.cba.common.exception.CbaException;

import java.util.Locale;
import java.util.UUID;

/**
 * Where the money for a loan payment comes from: a deposit account of the borrower
 * (the linked account unless another is named) or cash at an open teller till.
 */
public record LoanPaymentSource(Method method, UUID accountId, UUID tellerSessionId) {

    public enum Method { ACCOUNT, CASH }

    /**
     * Parses the request fields. {@code paymentMethod} defaults to ACCOUNT; CASH needs a
     * teller session. {@code accountId} may be null for ACCOUNT (the linked account).
     */
    public static LoanPaymentSource of(String paymentMethod, UUID accountId, UUID tellerSessionId) {
        Method method;
        try {
            method = paymentMethod == null || paymentMethod.isBlank()
                    ? Method.ACCOUNT : Method.valueOf(paymentMethod.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw CbaException.badRequest("INVALID_PAYMENT_METHOD",
                    "paymentMethod must be ACCOUNT or CASH, not " + paymentMethod);
        }
        if (method == Method.CASH && tellerSessionId == null) {
            throw CbaException.badRequest("TELLER_SESSION_REQUIRED",
                    "A cash payment must name the open teller session (tellerSessionId) receiving it");
        }
        return new LoanPaymentSource(method, method == Method.ACCOUNT ? accountId : null,
                method == Method.CASH ? tellerSessionId : null);
    }
}
