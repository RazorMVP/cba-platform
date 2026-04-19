package com.cba.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository repo;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LoginHistory record(String userId, String username,
                               String ipAddress, String userAgent,
                               LoginHistory.Status status,
                               String failureReason, String sessionRef) {
        return repo.save(LoginHistory.of(userId, username, ipAddress,
                userAgent, status, failureReason, sessionRef));
    }

    @Transactional(readOnly = true)
    public Page<LoginHistory> search(LoginHistory.Status status, String username,
                                     Instant from, Instant to, Pageable pageable) {
        return repo.search(
                status != null ? status.name() : null,
                username,
                from != null ? Timestamp.from(from) : null,
                to   != null ? Timestamp.from(to)   : null,
                pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long successCount  = repo.countByStatusSince(LoginHistory.Status.SUCCESS,  since);
        long failureCount  = repo.countByStatusSince(LoginHistory.Status.FAILURE,  since);
        long lockedCount   = repo.countByStatusSince(LoginHistory.Status.LOCKED,   since);
        long uniqueUsers   = repo.countDistinctUsersLoginSince(since);
        List<Object[]> top = repo.topFailedUsernames(Timestamp.from(since));

        List<Map<String, Object>> topFailedUsers = top.stream()
            .map(row -> Map.<String, Object>of(
                "username", row[0],
                "failureCount", ((Number) row[1]).longValue()))
            .toList();

        return Map.of(
            "periodDays",     days,
            "successLogins",  successCount,
            "failedLogins",   failureCount,
            "lockedAccounts", lockedCount,
            "uniqueUsers",    uniqueUsers,
            "topFailedUsers", topFailedUsers
        );
    }
}
