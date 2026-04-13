package com.cba.customer.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Payload for PUT /api/v1/customers/{id} — full profile update.
 * All fields are optional; only non-null values are applied.
 */
public record UpdateCustomerRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Email(message = "Invalid email address")
        String email,

        @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Invalid phone number")
        String phone,

        @Size(max = 50)
        String nationalId,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        String notes

) {}
