package util;

import entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;



public class JwtUtil {
    private static final String SECRET = "bXktdXJsLXNlY3JldC1rZXktdGhhdC1pcy1iYXNlNjQtZW5jb2RlZA==";
    private static final Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    private static final long EXPIRATION_MS = 3 * 60 * 60 * 1000;

    /**
     * اکنون subject برابر با userId است
     * و در کلِیم‌ها role و fullName قرار می‌گیرد
     */
    public static String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())              // userId as subject
                .claim("role", user.getRole().name())             // role claim
                .claim("fullName", user.getFullName())            // fullName claim
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static String getUserIdFromToken(String token) {
        try {
            return parseToken(token).getSubject();  // this is userId
        } catch (JwtException e) {
            return null;
        }
    }

    public static String getRoleFromToken(String token) {
        try {
            return parseToken(token).get("role", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public static String getFullNameFromToken(String token) {
        try {
            return parseToken(token).get("fullName", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public static Key getKey() {
        return key;
    }
}
