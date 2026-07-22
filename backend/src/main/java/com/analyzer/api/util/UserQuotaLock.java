package com.analyzer.api.util;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
@RequiredArgsConstructor
public class UserQuotaLock {

    private final JdbcTemplate jdbcTemplate;

    public void acquire(Long userId) {
        jdbcTemplate.execute((Connection con) -> {
            try (var statement = con.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, userId);
                statement.execute();
            }
            return null;
        });
    }
}
