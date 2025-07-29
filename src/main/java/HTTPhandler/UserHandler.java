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
import entity.OrderStatus;
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

public class UserHandler implements HttpHandler {
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
        System.out.println("Request sent");
        RegisterRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RegisterRequest.class
        );
        System.out.println(req.getRole());
        String EMAIL_REGEX      = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
        String MOBILE_REGEX = "^(?:\\+98|0)?9\\d{9}$";
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body"));
            return;
        }
        if (ErrorHandler.RateLackToken(ex)) return;
        if (req.getFull_name() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid full_name"));
            return;
        }
        if (req.getPhone() == null || !req.getPhone().matches(MOBILE_REGEX)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid phone"));
            return;
        }
        if(req.getEmail() != null && !req.getEmail().matches(EMAIL_REGEX)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid email"));
        }
        if (req.getPassword() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid password"));
            return;
        }
        if (req.getAddress() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid address"));
            return;
        }
        if (req.getRole() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid role"));
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
            Role RoleEnum;
            try {
                RoleEnum = Role.valueOf(req.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid Role: " + req.getRole()));
                return;
            }

            User user = new User();
            user.setFull_name(req.getFull_name());
            user.setPhone(req.getPhone());
            user.setEmail(req.getEmail());
            user.setPassword(req.getPassword());
            user.setRole(RoleEnum);
            user.setAddress(req.getAddress());
            user.setBank_info(req.getBank_info());
            user.setProfileImageBase64(req.getProfileImageBase64());
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
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body"));
            return;
        }
        if (req.getPhone() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid phone"));
            return;
        }
        if (req.getPassword() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid password"));
            return;
        }
        if (ErrorHandler.RateLackToken(ex)) return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery(
                            "from User where phone = :phone", User.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult();
            if (user == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("user not found"));
                return;
            }
            if(!user.getPassword().equals(req.getPassword())) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("password does not match"));
                return;
            }
            Role role = user.getRole();
            if (role == Role.SELLER || role == Role.COURIER) {
                if (user.getStatus() != UserStatus.APPROVED) {
                    JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
                    return;
                }
            }
            if (user.getStatus() == UserStatus.REJECTED) {
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
        String auth;
        try {
            auth = ex.getRequestHeaders().getFirst("Authorization");
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized request"));
            return;
        }
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
        String auth;
        try {
            auth = ex.getRequestHeaders().getFirst("Authorization");
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized request"));
            return;
        }
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        String userId = JwtUtil.getUserIdFromToken(token);
        EditProfileRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                EditProfileRequest.class
        );
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            assert userId != null;
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            if (req.getFull_name() != null) user.setFull_name(req.getFull_name());
            if (req.getAddress() != null) user.setAddress(req.getAddress());
            if (req.getEmail() != null) user.setEmail(req.getEmail());
            if (req.getProfileImageBase64() != null) user.setProfileImageBase64(req.getProfileImageBase64());
            if (req.getBank_info() != null) user.setBank_info(req.getBank_info());
            session.merge(user);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new ErrorResponse("Profile updated successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String auth;
        try {
            auth = ex.getRequestHeaders().getFirst("Authorization");
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized request"));
            return;
        }
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        TokenBlacklist.blacklistToken(token);
        JsonHelper.sendJson(ex, 200, new ErrorResponse("User logged out successfully"));
    }
}

