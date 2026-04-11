package com.cba.card.terminal;

/**
 * Response from the terminal simulator — decoded from the FEP's ISO 8583 response.
 */
public record SimulateResponse(
        /** ISO 8583 MTI of the request sent (e.g. "0100"). */
        String requestMti,

        /** ISO 8583 MTI of the response received (e.g. "0110"). */
        String responseMti,

        /** DE39 response code: "00"=approved, "51"=insufficient funds, etc. */
        String responseCode,

        /** Human-readable description of the response code. */
        String responseDescription,

        /** DE38 authorization code (6 chars) — present on approval only. */
        String authCode,

        /** Available balance from DE54 (on balance inquiry, may be null otherwise). */
        String availableBalance,

        /** DE11 STAN of the request. */
        String stan,

        /** DE37 RRN of the request. */
        String rrn,

        /** Whether the transaction was approved (responseCode == "00"). */
        boolean approved,

        /** Raw hex dump of the request message sent to FEP. */
        String requestHex,

        /** Raw hex dump of the response message received from FEP. */
        String responseHex
) {
    /** Returns a human-readable description for standard ISO 8583 DE39 codes. */
    public static String describeResponseCode(String rc) {
        if (rc == null) return "Unknown";
        return switch (rc) {
            case "00" -> "Approved";
            case "05" -> "Do Not Honor";
            case "12" -> "Invalid Transaction";
            case "14" -> "Invalid Card Number";
            case "30" -> "Format Error";
            case "41" -> "Lost Card";
            case "43" -> "Stolen Card";
            case "51" -> "Insufficient Funds";
            case "54" -> "Expired Card";
            case "55" -> "Incorrect PIN";
            case "57" -> "Transaction Not Permitted to Cardholder";
            case "62" -> "Restricted Card";
            case "91" -> "Issuer Unavailable";
            case "96" -> "System Malfunction";
            default   -> "Response Code " + rc;
        };
    }

    /** Builds an error response when the FEP could not be reached. */
    public static SimulateResponse fepUnavailable(String stan, String rrn, String requestHex) {
        return new SimulateResponse(
                null, null, "91", "Issuer Unavailable — FEP not reachable",
                null, null, stan, rrn, false, requestHex, null);
    }
}
