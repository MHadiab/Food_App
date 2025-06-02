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
import entity.UserStatus;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.ErrorResponse;
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
        String fullPath = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            String sub = fullPath.substring("/auth".length());
            if ("/register".equals(sub) && "POST".equalsIgnoreCase(method)) {
                handleRegister(ex);
                return;
            }
            if ("/login".equals(sub) && "POST".equalsIgnoreCase(method)) {
                handleLogin(ex);
                return;
            }
            if ("/logout".equals(sub) && "POST".equalsIgnoreCase(method)) {
                handleLogout(ex);
                return;
            }
            if ("/profile".equals(sub) && "GET".equalsIgnoreCase(method)) {
                handleGetProfile(ex);
                return;
            }
            if (sub.startsWith("/profile") && "PUT".equalsIgnoreCase(method)) {
                handleEditProfile(ex);
                return;
            }
            ex.sendResponseHeaders(404, -1);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleRegister(HttpExchange ex) throws IOException {

        RegisterRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RegisterRequest.class
        );
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body"));
            return;
        }

        // 4. چک فیلدهای ضروری
        System.out.println(req.getPassword());
        System.out.println(req.getRole());
        System.out.println(req.getAddress());
        if (req.getFull_name() == null || req.getPhone() == null
                || req.getPassword() == null || req.getRole() == null
                || req.getAddress() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Missing required fields"));
            return;
        }
        if (ErrorHandler.RateLackToken(ex)) return;
        if (req.getFull_name() == null || req.getPhone() == null ||
                req.getPassword() == null || req.getRole() == null ||
                req.getAddress() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            boolean phone_exists = session.createQuery(
                            "select 1 from User u where u.phone = :phone", Integer.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult() != null;
            boolean email_exists = session.createQuery(
                            "select 1 from User u where u.email = :email", String.class)
                    .setParameter("email", req.getEmail())
                    .uniqueResult() != null;
            if (phone_exists || email_exists) {
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
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                LoginRequest.class
        );
        if (ErrorHandler.RateLackToken(ex)) return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery(
                            "from User where phone = :phone", User.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult();
            if (user == null || !user.getPassword().equals(req.getPassword())) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                return;
            }
            Role role = user.getRole();
            if (role == Role.SELLER || role == Role.COURIER) {
                if (user.getStatus() != UserStatus.APPROVED) {
                    JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
                    return;
                }
            }
            if(user.getStatus() == UserStatus.REJECTED && user.getRole() == Role.BUYER) {
                JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
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
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        String userId = JwtUtil.getUserIdFromToken(token);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }
            UserInfo info = new UserInfo(user);
            JsonHelper.sendJson(ex, 200, info);
        }
    }

    private void handleEditProfile(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        String userId = JwtUtil.getUserIdFromToken(token);
        EditProfileRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                EditProfileRequest.class
        );
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }
            if (req.getFull_name() != null) user.setFull_name(req.getFull_name());
            if (req.getAddress() != null) user.setAddress(req.getAddress());
            if (req.getEmail() != null) user.setEmail(req.getEmail());
            if (req.getProfileImageBase64() != null) user.setProfileImageBase64(req.getProfileImageBase64());
            if (req.getBank_info() != null) user.setBank_info(req.getBank_info());
            session.merge(user);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Profile updated successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        TokenBlacklist.blacklistToken(token);
        JsonHelper.sendJson(ex, 200, new MessageResponse("User logged out successfully"));
    }
}

