package com.analyzer.api.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
