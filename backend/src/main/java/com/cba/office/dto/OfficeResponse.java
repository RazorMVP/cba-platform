package com.cba.office.dto;

import com.cba.office.Office;

import java.time.LocalDate;
import java.util.UUID;

public record OfficeResponse(
        UUID id,
        String name,
        String externalId,
        LocalDate openingDate,
        UUID parentId,
        String parentName,
        String hierarchy,
        String description,
        boolean active
) {
    public static OfficeResponse from(Office o) {
        return new OfficeResponse(
                o.getId(), o.getName(), o.getExternalId(), o.getOpeningDate(),
                o.getParent() != null ? o.getParent().getId() : null,
                o.getParent() != null ? o.getParent().getName() : null,
                o.getHierarchy(), o.getDescription(), o.isActive()
        );
    }
}
