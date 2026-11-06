package com.cba.openbanking;

public enum ConsentStatus {
    AWAITING_AUTHORISATION,
    AUTHORISED,
    /** A payment consent whose payment has been made; it can't be used again (UK OB "Consumed"). */
    CONSUMED,
    REVOKED,
    EXPIRED
}
