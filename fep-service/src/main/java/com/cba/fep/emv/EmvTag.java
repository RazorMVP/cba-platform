package com.cba.fep.emv;

/**
 * Well-known EMV tag identifiers from EMV Book 3 + EMVCo specifications.
 * Used as keys when parsing DE55 TLV data.
 */
public final class EmvTag {

    private EmvTag() {}

    // --- Application Interchange Profile ---
    public static final String APPLICATION_INTERCHANGE_PROFILE = "82";
    public static final String APPLICATION_USAGE_CONTROL       = "9F07";

    // --- Cryptogram ---
    public static final String APPLICATION_TRANSACTION_COUNTER = "9F36"; // ATC
    public static final String ARQC                            = "9F26"; // Application Request Cryptogram
    public static final String CRYPTOGRAM_INFORMATION_DATA     = "9F27"; // CID

    // --- Card risk management ---
    public static final String CARD_RISK_MANAGEMENT_DATA_1     = "9F13";
    public static final String CARD_RISK_MANAGEMENT_DATA_2     = "9F17";
    public static final String LOWER_CONSECUTIVE_OFFLINE_LIMIT = "9F14";
    public static final String UPPER_CONSECUTIVE_OFFLINE_LIMIT = "9F23";

    // --- Transaction data ---
    public static final String TERMINAL_VERIFICATION_RESULTS   = "95";  // TVR
    public static final String TERMINAL_COUNTRY_CODE           = "9F1A";
    public static final String TERMINAL_CAPABILITIES           = "9F33";
    public static final String ADDITIONAL_TERMINAL_CAPABILITIES = "9F40";
    public static final String TRANSACTION_DATE                = "9A";
    public static final String TRANSACTION_TYPE                = "9C";
    public static final String AMOUNT_AUTHORISED               = "9F02";
    public static final String AMOUNT_OTHER                    = "9F03";
    public static final String TRANSACTION_CURRENCY_CODE       = "5F2A";
    public static final String TRANSACTION_SEQUENCE_COUNTER    = "9F41";

    // --- Card data ---
    public static final String PAN                             = "5A";
    public static final String PAN_SEQUENCE_NUMBER             = "5F34";
    public static final String TRACK2_EQUIVALENT               = "57";
    public static final String APPLICATION_EXPIRY_DATE         = "5F24";
    public static final String ISSUER_APPLICATION_DATA         = "9F10"; // IAD

    // --- PIN ---
    public static final String CARDHOLDER_VERIFICATION_METHOD_RESULTS = "9F34"; // CVR

    // --- UnionPay QPBOC ---
    public static final String CUP_CUSTOMER_EXCLUSIVE_DATA     = "9F7C";
    public static final String CUP_VLP_FUNDS_LIMIT             = "9F77";
    public static final String CUP_VLP_TXN_LIMIT               = "9F78";
    public static final String CUP_VLP_AVAILABLE_FUNDS         = "9F79";

    // --- Visa ---
    public static final String VISA_VLP_ISSUER_AUTHORISATION_CODE = "9F76";
}
