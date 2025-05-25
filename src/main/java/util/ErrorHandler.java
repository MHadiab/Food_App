package util;

import com.sun.net.httpserver.HttpExchange;
import response.ErrorResponse;
import response.MessageResponse;

import java.io.IOException;

public class ErrorHandler {
    private static Boolean AuthorizationError(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized request"));
            return true;
        }
        return false;
    }
    private static Boolean TokenError(HttpExchange ex,String token) throws IOException {
        if (TokenBlacklist.isBlacklisted(token) || !JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized request"));
            return true;
        }
        return false;
    }
    private static Boolean ContentError(HttpExchange ex,String token) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new MessageResponse("Unsupported media type"));
            return true;
        }
        return false;
    }
    private static Boolean ManyRequestError(HttpExchange ex,String token) throws IOException {
        String userKey = JwtUtil.getUserIdFromToken(token);
        if (!RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new ErrorResponse("Too many requests"));
            return true;
        }
        return false;
    }
    public static boolean FindError(HttpExchange ex,String token) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        return ErrorHandler.AuthorizationError(ex) || ErrorHandler.TokenError(ex, token)
                || ErrorHandler.ContentError(ex, token) || ErrorHandler.ManyRequestError(ex, token);
    }
    public static boolean RateLackToken(HttpExchange ex) throws IOException {
        String clientIp = ex.getRemoteAddress()
                .getAddress()
                .getHostAddress();
        if (!RateLimiter.allowRequest(clientIp)) {
            JsonHelper.sendJson(ex, 429, new ErrorResponse("Too many requests"));
            return true;
        }
        return false;
    }
}
