package com.cba.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-entity search using JDBC to avoid coupling to every domain module.
 * Mirrors Mifos /search behaviour: searches clients, groups, loans, savings.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Set<String> ALLOWED_RESOURCES =
        Set.of("CLIENTS", "GROUPS", "LOANS", "SAVINGS");

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, String resource) {
        if (query == null || query.isBlank()) return List.of();
        if (resource != null && !resource.isBlank() && !ALLOWED_RESOURCES.contains(resource.toUpperCase(Locale.ROOT))) {
            throw new com.cba.common.exception.CbaException("INVALID_RESOURCE",
                "resource must be one of: " + ALLOWED_RESOURCES, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        List<SearchResult> results = new ArrayList<>();

        boolean all = resource == null || resource.isBlank();

        if (all || "CLIENTS".equalsIgnoreCase(resource)) {
            results.addAll(searchClients(like));
        }
        if (all || "LOANS".equalsIgnoreCase(resource)) {
            results.addAll(searchLoans(like));
        }
        if (all || "SAVINGS".equalsIgnoreCase(resource)) {
            results.addAll(searchAccounts(like));
        }
        if (all || "GROUPS".equalsIgnoreCase(resource)) {
            results.addAll(searchGroups(like));
        }
        return results;
    }

    private List<SearchResult> searchClients(String like) {
        String sql = """
            SELECT id, 'CLIENT' AS entity_type,
                   external_id, kyc_status AS status
            FROM customers
            WHERE external_id ILIKE ?
            LIMIT 20
            """;
        return jdbc.query(sql, (rs, i) -> new SearchResult(
            UUID.fromString(rs.getString("id")),
            "CLIENT",
            null,
            rs.getString("external_id"),
            rs.getString("status"),
            rs.getString("external_id")
        ), like);
    }

    private List<SearchResult> searchLoans(String like) {
        String sql = """
            SELECT id, 'LOAN' AS entity_type,
                   loan_account_number, status
            FROM loans
            WHERE LOWER(loan_account_number) LIKE ?
            LIMIT 20
            """;
        return jdbc.query(sql, (rs, i) -> new SearchResult(
            UUID.fromString(rs.getString("id")),
            "LOAN",
            null,
            rs.getString("loan_account_number"),
            rs.getString("status"),
            null
        ), like);
    }

    private List<SearchResult> searchAccounts(String like) {
        String sql = """
            SELECT id, 'SAVINGS' AS entity_type,
                   account_number, account_type, status
            FROM accounts
            WHERE LOWER(account_number) LIKE ?
            LIMIT 20
            """;
        return jdbc.query(sql, (rs, i) -> new SearchResult(
            UUID.fromString(rs.getString("id")),
            "SAVINGS",
            rs.getString("account_type"),
            rs.getString("account_number"),
            rs.getString("status"),
            null
        ), like);
    }

    private List<SearchResult> searchGroups(String like) {
        String sql = """
            SELECT id, 'GROUP' AS entity_type,
                   name, external_id, status
            FROM groups
            WHERE LOWER(name) LIKE ?
               OR LOWER(external_id) LIKE ?
            LIMIT 20
            """;
        return jdbc.query(sql, (rs, i) -> new SearchResult(
            UUID.fromString(rs.getString("id")),
            "GROUP",
            rs.getString("name"),
            rs.getString("external_id"),
            rs.getString("status"),
            rs.getString("external_id")
        ), like, like);
    }
}
