package com.cba.fep.scheme;

/**
 * Supported card schemes in the FEP.
 *
 * <p>Each scheme value corresponds to:
 * <ul>
 *   <li>A dedicated ISO 8583 packager XML ({@code iso8583-{name}.xml})</li>
 *   <li>A concrete {@link SchemeAdapter} implementation</li>
 *   <li>One or more BIN ranges registered in the card-service BIN table</li>
 * </ul>
 *
 * <p>Adding a new scheme requires:
 * <ol>
 *   <li>Add a value here</li>
 *   <li>Create {@code iso8583-{scheme}.xml} packager</li>
 *   <li>Implement {@link SchemeAdapter} for the new scheme</li>
 *   <li>Register in {@link SchemeAdapterFactory}</li>
 *   <li>Register BIN ranges via card-service admin API</li>
 * </ol>
 */
public enum SchemeType {

    /** Visa — BASE I / VisaNet / VIS extensions */
    VISA,

    /** Mastercard — MIP (Mastercard Interchange Processing) extensions */
    MASTERCARD,

    /** Verve — Interswitch Nigeria; settlement via NIBSS */
    VERVE,

    /** Afrigo — PAPSS Pan-African Payment and Settlement System */
    AFRIGO,

    /** China UnionPay — CUPS/UICS with QPBOC contactless */
    UNIONPAY,

    /** Unknown scheme — used before BIN lookup or when BIN is unregistered */
    UNKNOWN
}
