package com.cba.search;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Global Search", description = "Cross-entity keyword search across clients, loans, savings accounts and groups — mirrors the Mifos /search endpoint")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search across all entity types; filter to one with ?resource=CLIENTS|LOANS|SAVINGS|GROUPS")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<SearchResult>> search(
            @RequestParam String query,
            @RequestParam(required = false) String resource) {
        return ApiResponse.ok(searchService.search(query, resource));
    }
}
