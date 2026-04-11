package com.cba.fep.auth;

/**
 * ISO 8583 response codes used in DE39.
 *
 * <p>These are the standard codes defined in ISO 8583-1987 and universally
 * adopted by all card schemes. Scheme-specific extensions exist (e.g., Visa
 * uses 1Y for offline decline) but are not modelled here.
 */
public final class ResponseCode {

    private ResponseCode() {}

    // --- Approval ---
    public static final String APPROVED                            = "00";
    public static final String APPROVED_HONOUR_WITH_IDENTIFICATION = "08";
    public static final String APPROVED_PARTIAL_AMOUNT            = "10";
    public static final String APPROVED_VIP                       = "11";

    // --- Referral ---
    public static final String CALL_ISSUER                        = "01";
    public static final String CALL_ISSUER_SPECIAL_CONDITION      = "02";
    public static final String INVALID_MERCHANT                   = "03";
    public static final String PICK_UP_CARD                       = "04";
    public static final String DO_NOT_HONOUR                      = "05";
    public static final String ERROR                              = "06";
    public static final String PICK_UP_CARD_SPECIAL_CONDITION     = "07";

    // --- Decline ---
    public static final String INVALID_TRANSACTION                = "12";
    public static final String INVALID_AMOUNT                     = "13";
    public static final String INVALID_CARD_NUMBER                = "14";
    public static final String NO_SUCH_ISSUER                     = "15";
    public static final String TRANSACTION_NOT_PERMITTED_TO_CARD  = "57";
    public static final String TRANSACTION_NOT_PERMITTED_TO_TERM  = "58";
    public static final String SUSPECT_FRAUD                      = "59";
    public static final String EXCEEDED_LIMIT                     = "61";
    public static final String RESTRICTED_CARD                    = "62";
    public static final String SECURITY_VIOLATION                 = "63";
    public static final String EXCEEDS_WITHDRAWAL_LIMIT           = "65";
    public static final String LOST_CARD                          = "41";
    public static final String STOLEN_CARD                        = "43";
    public static final String INSUFFICIENT_FUNDS                 = "51";
    public static final String NO_CHECKING_ACCOUNT                = "52";
    public static final String NO_SAVINGS_ACCOUNT                 = "53";
    public static final String EXPIRED_CARD                       = "54";
    public static final String INCORRECT_PIN                      = "55";
    public static final String CARD_NOT_EFFECTIVE                 = "56";
    public static final String PIN_TRIES_EXCEEDED                 = "75";

    // --- System errors ---
    public static final String FORMAT_ERROR                       = "30";
    public static final String BANK_NOT_FOUND                     = "31";
    public static final String DUPLICATE_TRANSACTION              = "94";
    public static final String SYSTEM_MALFUNCTION                 = "96";
    public static final String ROUTE_NOT_FOUND                    = "92";

    // --- Reversal / advice ---
    public static final String REVERSAL_ACCEPTED                  = "00";
    public static final String ORIGINAL_NOT_FOUND                 = "25";
    public static final String RECONCILIATION_ERROR               = "95";
}
