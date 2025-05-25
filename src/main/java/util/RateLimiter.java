package util;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    // هر IP یا userId به تعداد مشخصی توکن دارد
    private static final Map<String, SimpleRateBucket> buckets = new ConcurrentHashMap<>();

    public static boolean allowRequest(String key) {
        // tryConsume()==true یعنی اجازه هست
        return buckets
                .computeIfAbsent(key, k -> new SimpleRateBucket(10, Duration.ofMinutes(1)))
                .tryConsume();
    }

    public static class SimpleRateBucket {
        private final int capacity;
        private final Duration refillPeriod;
        private int tokens;
        private Instant lastRefill;

        public SimpleRateBucket(int capacity, Duration refillPeriod) {
            this.capacity     = capacity;
            this.refillPeriod = refillPeriod;
            this.tokens       = capacity;
            this.lastRefill   = Instant.now();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
        private void refill() {
            Instant now = Instant.now();
            long periods = ChronoUnit.MILLIS.between(lastRefill, now) / refillPeriod.toMillis();
            if (periods > 0) {
                tokens = Math.min(capacity, tokens + (int)periods * capacity);
                lastRefill = now;
            }
        }
    }
}


