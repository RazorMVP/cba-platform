package com.cba.system.bureau;

/**
 * Normalised result of a credit-bureau pull, independent of any specific bureau's wire
 * format. A {@code UNAVAILABLE} status (bureau down / not configured) is a first-class
 * outcome, never an exception — the caller decides whether that blocks a loan.
 */
public record CreditReport(Status status, int score, String band, String reference, String message) {

    public enum Status {
        /** A record was found and scored. */
        HIT,
        /** Bureau reachable but no file on this subject (thin/no file). */
        NO_HIT,
        /** Bureau unreachable, errored, or not configured. */
        UNAVAILABLE
    }

    public static CreditReport hit(int score, String reference) {
        return new CreditReport(Status.HIT, score, bandFor(score), reference, null);
    }

    public static CreditReport noHit(String reference) {
        return new CreditReport(Status.NO_HIT, 0, "NO_FILE", reference, "No bureau file for subject");
    }

    public static CreditReport unavailable(String message) {
        return new CreditReport(Status.UNAVAILABLE, 0, "UNKNOWN", null, message);
    }

    /** FICO-style band from a 300–850 score. */
    public static String bandFor(int score) {
        if (score >= 800) return "EXCELLENT";
        if (score >= 740) return "VERY_GOOD";
        if (score >= 670) return "GOOD";
        if (score >= 580) return "FAIR";
        return "POOR";
    }
}
