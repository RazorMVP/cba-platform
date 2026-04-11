package com.cba.fep.iso;

/**
 * ISO 8583-1987 Data Element constants.
 *
 * Field numbers are used throughout the FEP to get/set values on ISOMsg objects.
 * Using typed constants instead of bare integers catches typos at compile time.
 */
public final class IsoField {

    private IsoField() {}

    // --- Core transaction fields ---
    public static final int MTI                        = 0;
    public static final int BITMAP_PRIMARY             = 1;
    public static final int PAN                        = 2;
    public static final int PROCESSING_CODE            = 3;
    public static final int AMOUNT_TRANSACTION         = 4;
    public static final int AMOUNT_SETTLEMENT          = 5;
    public static final int AMOUNT_BILLING             = 6;
    public static final int TRANSMISSION_DATETIME      = 7;
    public static final int AMOUNT_BILLING_FEE         = 8;
    public static final int CONVERSION_RATE_SETTLEMENT = 9;
    public static final int CONVERSION_RATE_BILLING    = 10;
    public static final int STAN                       = 11;
    public static final int LOCAL_TIME                 = 12;
    public static final int LOCAL_DATE                 = 13;
    public static final int EXPIRY_DATE                = 14;
    public static final int SETTLEMENT_DATE            = 15;
    public static final int CONVERSION_DATE            = 16;
    public static final int CAPTURE_DATE               = 17;
    public static final int MCC                        = 18;
    public static final int ACQUIRING_COUNTRY          = 19;
    public static final int PAN_EXTENDED_COUNTRY       = 20;
    public static final int FORWARDING_COUNTRY         = 21;
    public static final int POS_ENTRY_MODE             = 22;
    public static final int CARD_SEQUENCE_NUMBER       = 23;
    public static final int NETWORK_ID                 = 24;
    public static final int POS_CONDITION_CODE         = 25;
    public static final int POS_PIN_CAPTURE_CODE       = 26;
    public static final int AUTH_ID_RESPONSE_LENGTH    = 27;

    // --- Institution IDs ---
    public static final int ACQUIRING_INSTITUTION_ID   = 32;
    public static final int FORWARDING_INSTITUTION_ID  = 33;
    public static final int PAN_EXTENDED               = 34;

    // --- Track data ---
    public static final int TRACK2_DATA                = 35;
    public static final int TRACK3_DATA                = 36;

    // --- Auth / response fields ---
    public static final int RETRIEVAL_REF_NUMBER       = 37;
    public static final int AUTH_ID_RESPONSE           = 38;
    public static final int RESPONSE_CODE              = 39;
    public static final int SERVICE_RESTRICTION_CODE   = 40;

    // --- Terminal / merchant ---
    public static final int TERMINAL_ID                = 41;
    public static final int CARD_ACCEPTOR_ID           = 42;
    public static final int CARD_ACCEPTOR_NAME         = 43;
    public static final int ADDITIONAL_RESPONSE_DATA   = 44;
    public static final int TRACK1_DATA                = 45;
    public static final int ADDITIONAL_DATA_ISO        = 46;
    public static final int ADDITIONAL_DATA_NATIONAL   = 47;
    public static final int ADDITIONAL_DATA_PRIVATE    = 48;

    // --- Currency fields ---
    public static final int CURRENCY_TRANSACTION       = 49;
    public static final int CURRENCY_SETTLEMENT        = 50;
    public static final int CURRENCY_BILLING           = 51;

    // --- Security fields ---
    public static final int PIN_DATA                   = 52;
    public static final int SECURITY_CONTROL_INFO      = 53;
    public static final int ADDITIONAL_AMOUNTS         = 54;
    public static final int ICC_DATA                   = 55;  // EMV / QPBOC TLV

    // --- Private / reserved DEs ---
    public static final int RESERVED_PRIVATE_60        = 60;
    public static final int RESERVED_PRIVATE_61        = 61;
    public static final int RESERVED_PRIVATE_62        = 62;
    public static final int RESERVED_PRIVATE_63        = 63;

    // --- Integrity / bitmap extension ---
    public static final int MAC_PRIMARY                = 64;
    public static final int BITMAP_TERTIARY            = 65;
    public static final int NETWORK_MANAGEMENT_CODE    = 70;

    // --- Reversal / replacement ---
    public static final int ORIGINAL_DATA_ELEMENTS     = 90;
    public static final int REPLACEMENT_AMOUNTS        = 95;
    public static final int MESSAGE_SECURITY_CODE      = 96;

    // --- Receiving institution ---
    public static final int RECEIVING_INSTITUTION_ID   = 100;
    public static final int ACCOUNT_ID_1               = 102;
    public static final int ACCOUNT_ID_2               = 103;

    // --- Mastercard MIP extended fields (111–127) ---
    public static final int MC_MIP_111                 = 111;
    public static final int MC_MIP_112                 = 112;
    public static final int MC_MIP_113                 = 113;
    public static final int MC_MIP_114                 = 114;
    public static final int MC_MIP_115                 = 115;
    public static final int MC_MIP_116                 = 116;
    public static final int MC_MIP_117                 = 117;
    public static final int MC_MIP_118                 = 118;
    public static final int MC_MIP_119                 = 119;
    public static final int MC_MIP_120                 = 120;
    public static final int MC_MIP_121                 = 121;
    public static final int MC_MIP_122                 = 122;
    public static final int MC_MIP_123                 = 123;
    public static final int MC_MIP_124                 = 124;
    public static final int MC_MIP_125                 = 125;

    // --- Visa network usage (126) ---
    public static final int VISA_NETWORK_USAGE         = 126;

    // --- Secondary MAC ---
    public static final int MAC_SECONDARY              = 128;
}
