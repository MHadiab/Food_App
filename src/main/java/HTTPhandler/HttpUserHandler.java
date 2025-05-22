package HTTPhandler;
import com.google.gson.Gson;
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
import util.HibernateUtil;
import util.JsonHelper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import entity.User;
import util.JwtUtil;
import util.TokenBlacklist;

public class HttpUserHandler implements HttpHandler {
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            switch (path) {
                case "/auth/register":
                    if ("POST".equalsIgnoreCase(method)) handleRegister(exchange);
                    break;
                case "/auth/login":
                    if ("POST".equalsIgnoreCase(method)) handleLogin(exchange);
                    break;
                case "/auth/profile":
                    if ("GET".equalsIgnoreCase(method)) handleGetProfile(exchange);
                    else if ("PUT".equalsIgnoreCase(method)) handleEditProfile(exchange);
                    break;
                case "/auth/logout":
                    if ("POST".equalsIgnoreCase(method)) handleLogout(exchange);
                    break;
                default:
                    exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void handleRegister(HttpExchange ex) throws IOException {
        RegisterRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RegisterRequest.class
        );
        if (req.getFull_name() == null || req.getPhone() == null ||
                req.getPassword() == null || req.getRole() == null ||
                req.getAddress() == null) {
            JsonHelper.sendJson(ex,400,new MessageResponse("Invalid input data"));
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
            // تصویر پروفایل: باید ذخیره‌سازی کنی و URL را اینجا ست کنی
//            user.setProfileImageUrl(storeImage(req.getProfileImageBase64()));
            session.persist(user);
            tx.commit();

            String token = JwtUtil.generateToken(user);

            RegisterResponse resp = new RegisterResponse(
                    "User registered successfully",
                    user.getUser_id().toString(),
                    token
            );
            JsonHelper.sendJson(ex, 201, resp);

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
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery(
                            "from User where phone = :phone", User.class)
                    .setParameter("phone", req.getPhone())
                    .uniqueResult();
            if (user == null || !user.getPassword().equals(req.getPassword())) {
                JsonHelper.sendJson(ex,401,new MessageResponse("Invalid phone or password"));
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
                    "Login successful",
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

        assert auth != null;
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("nvalid input"));
            return;
        }

        String userId = JwtUtil.getUserIdFromToken(token);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
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
        String token = auth.substring(7);
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
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid input"));
            return;
        }
        String userId = JwtUtil.getUserIdFromToken(token);
        TokenBlacklist.blacklistToken(token);
        JsonHelper.sendJson(ex, 200, new MessageResponse("User logged out successfully"));
    }
}

