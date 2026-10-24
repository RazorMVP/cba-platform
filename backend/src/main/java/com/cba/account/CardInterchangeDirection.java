package com.cba.account;

/**
 * Which way card interchange flows for the issuer. On a purchase the acquirer pays the issuer
 * (EARNED, income); on cash at another bank's ATM the issuer pays the acquirer (PAID, expense).
 */
public enum CardInterchangeDirection {
    EARNED,
    PAID
}
