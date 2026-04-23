package com.cba.search;

import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService — unit tests")
class SearchServiceTest {

    @Mock JdbcTemplate jdbc;
    @InjectMocks SearchService searchService;

    private static final UUID ID = UUID.randomUUID();

    @Nested
    @DisplayName("Query Guard")
    class QueryGuard {

        @Test
        @DisplayName("null query returns empty list without hitting DB")
        void nullQuery_returnsEmpty() {
            assertThat(searchService.search(null, null)).isEmpty();
            verifyNoInteractions(jdbc);
        }

        @Test
        @DisplayName("blank query returns empty list without hitting DB")
        void blankQuery_returnsEmpty() {
            assertThat(searchService.search("   ", null)).isEmpty();
            verifyNoInteractions(jdbc);
        }

        @Test
        @DisplayName("invalid resource throws CbaException")
        void invalidResource_throws() {
            assertThatThrownBy(() -> searchService.search("john", "INVOICES"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("resource must be one of");
        }

        @Test
        @DisplayName("valid resource string is case-insensitive")
        void resource_caseInsensitive() {
            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of());
            assertThatCode(() -> searchService.search("test", "clients")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Scoped Search")
    class ScopedSearch {

        @Test
        @DisplayName("CLIENTS resource returns client results")
        void clientsResource_returnsClients() {
            SearchResult result = new SearchResult(ID, "CLIENT", null, "EXT-001", "ACTIVE", "EXT-001");
            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(result));

            List<SearchResult> results = searchService.search("ext-001", "CLIENTS");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).entityType()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("LOANS resource returns loan results")
        void loansResource_returnsLoans() {
            SearchResult result = new SearchResult(ID, "LOAN", null, "LN-001", "ACTIVE", null);
            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(result));

            List<SearchResult> results = searchService.search("LN-001", "LOANS");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).entityType()).isEqualTo("LOAN");
        }

        @Test
        @DisplayName("SAVINGS resource returns account results")
        void savingsResource_returnsAccounts() {
            SearchResult result = new SearchResult(ID, "SAVINGS", "SAVINGS", "ACC-001", "ACTIVE", null);
            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(result));

            List<SearchResult> results = searchService.search("ACC", "SAVINGS");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).entityType()).isEqualTo("SAVINGS");
        }

        @Test
        @DisplayName("GROUPS resource passes like param twice (name OR external_id)")
        void groupsResource_returnsGroups() {
            SearchResult result = new SearchResult(ID, "GROUP", "Test Group", "GRP-001", "ACTIVE", "GRP-001");
            when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenReturn(List.of(result));

            List<SearchResult> results = searchService.search("test", "GROUPS");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).entityType()).isEqualTo("GROUP");
        }
    }

    @Nested
    @DisplayName("All-Resource Search")
    class AllResourceSearch {

        @Test
        @DisplayName("null resource searches all four entities and aggregates")
        void nullResource_searchesAll() {
            SearchResult hit = new SearchResult(ID, "CLIENT", null, "EXT-001", "ACTIVE", "EXT-001");
            SearchResult groupHit = new SearchResult(UUID.randomUUID(), "GROUP", "Test", "GRP-001", "ACTIVE", "GRP-001");

            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(hit));
            when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenReturn(List.of(groupHit));

            List<SearchResult> results = searchService.search("test", null);
            // clients + loans + savings = 3 hits from single-param stub; groups = 1 hit
            assertThat(results).hasSize(4);
        }

        @Test
        @DisplayName("blank resource string searches all four entities")
        void blankResource_searchesAll() {
            when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of());
            when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenReturn(List.of());

            List<SearchResult> results = searchService.search("xyz", "  ");
            assertThat(results).isEmpty();
        }
    }
}
