package com.cba.system;

import com.cba.common.exception.CbaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Field Configuration", description = "Per-entity field visibility and validation rules — enable, disable or make fields mandatory for CLIENT, ADDRESS and LOAN entity types")
@RestController
@RequestMapping("/api/v1/fieldconfiguration")
@RequiredArgsConstructor
public class FieldConfigurationController {

    private final FieldConfigurationRepository repo;

    @Operation(summary = "List all field configurations across all entity types")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<FieldConfiguration>> listAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    @Operation(summary = "List field configurations for a specific entity type")
    @GetMapping("/{entityType}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<FieldConfiguration>> listByEntity(@PathVariable String entityType) {
        return ResponseEntity.ok(
            repo.findByEntityTypeOrderByDisplayOrderAsc(entityType.toUpperCase())
        );
    }

    @Operation(summary = "Get field configuration for a specific entity type and field name")
    @GetMapping("/{entityType}/{fieldName}")
    @Transactional(readOnly = true)
    public ResponseEntity<FieldConfiguration> getField(
            @PathVariable String entityType,
            @PathVariable String fieldName) {
        return ResponseEntity.ok(
            repo.findByEntityTypeAndFieldName(entityType.toUpperCase(), fieldName)
                .orElseThrow(() -> CbaException.notFound("FIELD_CONFIG_NOT_FOUND",
                    "No field configuration for " + entityType + "/" + fieldName))
        );
    }

    @Operation(summary = "Update a field configuration entry (label, enabled, mandatory, order)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<FieldConfiguration> update(
            @PathVariable UUID id,
            @RequestBody UpdateRequest req) {
        FieldConfiguration fc = repo.findById(id)
                .orElseThrow(() -> CbaException.notFound("FIELD_CONFIG_NOT_FOUND", "Field configuration not found"));
        if (req.fieldLabel()    != null) fc.setFieldLabel(req.fieldLabel());
        if (req.enabled()       != null) fc.setEnabled(req.enabled());
        if (req.mandatory()     != null) fc.setMandatory(req.mandatory());
        if (req.displayOrder()  != null) fc.setDisplayOrder(req.displayOrder());
        if (req.description()   != null) fc.setDescription(req.description());
        return ResponseEntity.ok(repo.save(fc));
    }

    @Operation(summary = "Add a custom field configuration entry")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<FieldConfiguration> create(@RequestBody CreateRequest req) {
        if (repo.existsByEntityTypeAndFieldName(req.entityType().toUpperCase(), req.fieldName())) {
            throw CbaException.conflict("FIELD_CONFIG_EXISTS",
                "Field configuration for " + req.entityType() + "/" + req.fieldName() + " already exists");
        }
        FieldConfiguration fc = new FieldConfiguration();
        fc.setEntityType(req.entityType().toUpperCase());
        fc.setFieldName(req.fieldName());
        fc.setFieldLabel(req.fieldLabel());
        fc.setEnabled(req.enabled() != null ? req.enabled() : true);
        fc.setMandatory(req.mandatory() != null ? req.mandatory() : false);
        fc.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
        fc.setDescription(req.description());
        return ResponseEntity.status(201).body(repo.save(fc));
    }

    @Operation(summary = "Delete a field configuration entry")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) {
            throw CbaException.notFound("FIELD_CONFIG_NOT_FOUND", "Field configuration not found");
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    record UpdateRequest(String fieldLabel, Boolean enabled, Boolean mandatory,
                         Integer displayOrder, String description) {}

    record CreateRequest(String entityType, String fieldName, String fieldLabel,
                         Boolean enabled, Boolean mandatory,
                         Integer displayOrder, String description) {}
}
