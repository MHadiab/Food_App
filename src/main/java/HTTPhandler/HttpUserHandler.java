package HTTPhandler;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.EditProfileRequest;
import dto.LoginRequest;
import dto.RegisterRequest;
import dto.UserInfo;
import entity.Role;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.LoginResponse;
import response.MessageResponse;
import response.RegisterResponse;
import util.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import entity.User;

public class HttpUserHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if ("/auth/register".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleRegister(ex);
            }
            else if ("/auth/login".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleLogin(ex);
            }
            else if (path.matches("/auth/profile(/\\d+)?")) {
                if ("GET".equalsIgnoreCase(method))      handleGetProfile(ex);
                else if ("PUT".equalsIgnoreCase(method)) handleEditProfile(ex);
                else ex.sendResponseHeaders(405, -1);
            }
            else if ("/auth/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleLogout(ex);
            }
            else {
                ex.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ex.sendResponseHeaders(500, -1);
        }
    }

    private void handleRegister(HttpExchange ex) throws IOException {

        RegisterRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RegisterRequest.class
        );

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        String clientIp = ex.getRemoteAddress()
                .getAddress()
                .getHostAddress();
        if (RateLimiter.allowRequest(clientIp)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }

        if (req.getFull_name() == null || req.getPhone() == null ||
                req.getPassword() == null || req.getRole() == null ||
                req.getAddress() == null) {
            JsonHelper.sendJson(ex,400,new MessageResponse("Invalid input"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            boolean exists = session.createQuery(
                            "select 1 from User u where u.phone = :phone", Integer.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult() != null;
            if (exists) {
                JsonHelper.sendJson(ex, 409, new MessageResponse("Phone number already exists"));
                return;
            }

            User user = new User();
            user.setFull_name(req.getFull_name());
            user.setPhone(req.getPhone());
            user.setEmail(req.getEmail());
            user.setPassword(req.getPassword()); // در عمل هش کن
            user.setRole(Role.valueOf(req.getRole().toUpperCase()));
            user.setAddress(req.getAddress());
            user.setBank_info(req.getBank_info());

            session.persist(user);
            tx.commit();

            String token = JwtUtil.generateToken(user);

            RegisterResponse resp = new RegisterResponse(
                    "User registered successfully",
                    user.getUser_id().toString(),
                    token
            );
            JsonHelper.sendJson(ex, 200, resp);

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        LoginRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(),StandardCharsets.UTF_8),
                LoginRequest.class
        );

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        String clientIp = ex.getRemoteAddress()
                .getAddress()
                .getHostAddress();
        if (RateLimiter.allowRequest(clientIp)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery(
                            "from User where phone = :phone", User.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult();
            if (user == null || !user.getPassword().equals(req.getPassword())) {
                JsonHelper.sendJson(ex,400,new MessageResponse("Invalid input"));
                return;
            }
            String token = JwtUtil.generateToken(user);

            UserInfo info = new UserInfo(
                    user.getUser_id().toString(),
                    user.getFull_name(),
                    user.getPhone(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getAddress(),
                    user.getProfileImageBase64(),
                    user.getBank_info()
            );
            LoginResponse resp = new LoginResponse(
                    "User logged in successfully",
                    token,
                    info
            );
            JsonHelper.sendJson(ex, 200, resp);
        } catch (IOException e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleGetProfile(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        assert auth != null;
        String token = auth.substring(7);

        String userKey = JwtUtil.getUserIdFromToken(token);
        if (RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }


        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
            return;
        }

        String userId = JwtUtil.getUserIdFromToken(token);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            assert userId != null;
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }
            UserInfo resp = new UserInfo(
                    user.getUser_id().toString(),
                    user.getFull_name(),
                    user.getPhone(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getAddress(),
                    user.getProfileImageBase64(),
                    user.getBank_info()
            );
            JsonHelper.sendJson(ex, 200, resp);
        }
    }

    private void handleEditProfile(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        String token = auth.substring(7);
        String userKey = JwtUtil.getUserIdFromToken(token);
        if (RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }

        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
            return;
        }

        String userId = JwtUtil.getUserIdFromToken(token);
        EditProfileRequest req=GSON.fromJson(new InputStreamReader(ex.getRequestBody(),StandardCharsets.UTF_8),EditProfileRequest.class);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }
            if (req.getFull_name() != null) user.setFull_name(req.getFull_name());
            if (req.getAddress()  != null) user.setAddress(req.getAddress());
            if (req.getBank_info() != null) user.setBank_info(req.getBank_info());
            if (req.getEmail() != null) user.setEmail(req.getEmail());
            if (req.getProfileImageBase64() != null) {
                user.setProfileImageBase64(req.getProfileImageBase64());
            }
            session.merge(user);
            tx.commit();

        }

    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex,401,new MessageResponse("Unauthorized"));
            return;
        }
        String token = auth.substring(7);

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        String userKey = JwtUtil.getUserIdFromToken(token);
        if (RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }

        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid input"));
            return;
        }
        String userId = JwtUtil.getUserIdFromToken(token);
        TokenBlacklist.blacklistToken(token);
        JsonHelper.sendJson(ex, 200, new MessageResponse("User logged out successfully"));
    }
}

