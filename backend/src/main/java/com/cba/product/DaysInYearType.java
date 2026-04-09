package com.cba.product;

public enum DaysInYearType {
    ACTUAL,    // 365 or 366 (leap year)
    DAYS_360,  // 30/360 convention
    DAYS_364,  // 52 weeks
    DAYS_365   // Fixed 365
}
