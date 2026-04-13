package com.cba.customer;

import com.cba.customer.dto.CreateCustomerRequest;
import com.cba.customer.dto.CustomerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    // Lifecycle dates — set by service commands, not on create
    @Mapping(target = "activationDate", ignore = true)
    @Mapping(target = "closureDate", ignore = true)
    @Mapping(target = "rejectionDate", ignore = true)
    @Mapping(target = "withdrawalDate", ignore = true)
    // Lifecycle reasons — set by service commands
    @Mapping(target = "closureReason", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "withdrawalReason", ignore = true)
    // Staff / office — set by assignStaff command
    @Mapping(target = "staffId", ignore = true)
    @Mapping(target = "officeId", ignore = true)
    // Transfer fields — set by proposeTransfer command
    @Mapping(target = "transferToOfficeId", ignore = true)
    @Mapping(target = "transferDate", ignore = true)
    @Mapping(target = "transferNote", ignore = true)
    Customer toEntity(CreateCustomerRequest request);

    CustomerResponse toResponse(Customer customer);
}
