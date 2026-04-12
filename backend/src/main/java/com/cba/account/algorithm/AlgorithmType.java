package com.cba.account.algorithm;

/**
 * Identifies which account number algorithm is in use.
 * New country algorithms are added here — no changes to the framework itself.
 */
public enum AlgorithmType {

    /** Default Mifos-style: {branch}-{typeCode}-{7-digit-sequence}  e.g. 001-SAV-0001234 */
    MIFOS,

    /**
     * Nigerian Uniform Bank Account Number (NUBAN).
     * CBN mandate — 10-digit format: {bankCode(3)}{serial(6)}{checkDigit(1)}
     * Check digit uses weights {3,7,3,3,7,3,3,7,3}.
     */
    NUBAN
}
