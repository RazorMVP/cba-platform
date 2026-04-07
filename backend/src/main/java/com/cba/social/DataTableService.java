package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataTableService {

    public record ColumnRequest(
        String columnName, String columnType, Integer columnLength,
        boolean nullable, boolean unique, UUID codeId
    ) {}

    public record CreateDataTableRequest(
        String registeredTableName,
        String applicationTableName,
        boolean allowMultipleRows,
        List<ColumnRequest> columns
    ) {}

    private final DataTableRepository dataTableRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<DataTable> listDataTables(String applicationTableName, Pageable p) {
        if (applicationTableName != null)
            return dataTableRepository.findByApplicationTableName(applicationTableName, p);
        return dataTableRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public DataTable getDataTable(UUID id) {
        return dataTableRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("DataTable", id));
    }

    @Transactional
    public DataTable createDataTable(CreateDataTableRequest req) {
        if (dataTableRepository.existsByRegisteredTableName(req.registeredTableName()))
            throw CbaException.conflict("DATATABLE_EXISTS",
                "DataTable '" + req.registeredTableName() + "' already exists");
        DataTable dt = new DataTable();
        dt.setRegisteredTableName(req.registeredTableName());
        dt.setApplicationTableName(req.applicationTableName());
        dt.setAllowMultipleRows(req.allowMultipleRows());
        if (req.columns() != null) {
            for (ColumnRequest col : req.columns()) {
                DataTableColumn c = new DataTableColumn();
                c.setDataTable(dt);
                c.setColumnName(col.columnName());
                c.setColumnType(col.columnType());
                c.setColumnLength(col.columnLength());
                c.setNullable(col.nullable());
                c.setUnique(col.unique());
                c.setCodeId(col.codeId());
                dt.getColumns().add(c);
            }
        }
        DataTable saved = dataTableRepository.save(dt);
        auditLogService.log("DataTable", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteDataTable(UUID id) {
        DataTable dt = getDataTable(id);
        dataTableRepository.delete(dt);
        auditLogService.log("DataTable", id.toString(), "DELETE", null, null);
    }
}
