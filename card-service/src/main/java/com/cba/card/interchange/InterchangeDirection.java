package com.cba.card.interchange;

/**
 * Which way interchange flows for the issuer: on a purchase the acquirer pays the issuer
 * (EARNED, income); on cash at another bank's ATM the issuer pays the acquirer (PAID, expense).
 */
public enum InterchangeDirection {
    EARNED,
    PAID
}
