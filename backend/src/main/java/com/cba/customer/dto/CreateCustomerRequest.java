package com.cba.customer.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateCustomerRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Email is required")
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
