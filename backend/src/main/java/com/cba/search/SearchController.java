package com.cba.search;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Global search across clients, groups, accounts, loans — mirrors Mifos /search endpoint.
 * Supports entity-type filtering via ?resource= (CLIENTS,GROUPS,LOANS,SAVINGS).
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<SearchResult>> search(
            @RequestParam String query,
            @RequestParam(required = false) String resource) {
        return ApiResponse.ok(searchService.search(query, resource));
    }
}
