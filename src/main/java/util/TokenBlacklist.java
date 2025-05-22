package util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * یک لیست ساده‌ی in-memory برای توکن‌های باطل‌شده
 */
public class TokenBlacklist {
    private static final Set<String> BLACKLIST = ConcurrentHashMap.newKeySet();

    public static void blacklistToken(String token) {
        BLACKLIST.add(token);
    }
    public static boolean isBlacklisted(String token) {
        return BLACKLIST.contains(token);
    }
}
