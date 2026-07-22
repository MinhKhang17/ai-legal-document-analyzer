package com.analyzer.api.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

// Single source of truth for "now" in subscription/billing business logic, so expiry and
// downgrade timing doesn't depend on the JVM default timezone, which can differ between
// app instances/environments and silently shift when a plan transitions.
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
