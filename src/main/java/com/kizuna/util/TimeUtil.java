package com.kizuna.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtil {
    private TimeUtil() { }
    public static long toEpochMillis(LocalDateTime value) { return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public static LocalDateTime fromEpochMillis(long value) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()); }
}
