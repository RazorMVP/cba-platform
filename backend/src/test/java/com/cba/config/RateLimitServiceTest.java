package com.cba.config;

import com.cba.system.GlobalConfigurationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService.firstEventInWindow — once-per-window dedup")
class RateLimitServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock GlobalConfigurationRepository globalConfigRepo;

    @InjectMocks RateLimitService service;

    @Test
    @DisplayName("first caller in the window → true (SETNX succeeded)")
    void firstCall_true() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("rlevt:evt:id"), eq("1"), any(Duration.class))).thenReturn(true);

        assertThat(service.firstEventInWindow("evt:id")).isTrue();
    }

    @Test
    @DisplayName("subsequent caller in the same window → false (key already present)")
    void subsequent_false() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertThat(service.firstEventInWindow("evt:id")).isFalse();
    }

    @Test
    @DisplayName("Redis unavailable → false (suppress the event rather than risk a storm)")
    void redisDown_false() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThat(service.firstEventInWindow("evt:id")).isFalse();
    }
}
