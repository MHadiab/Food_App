package HTTPhandler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.EditUserRequest;
import entity.Response;
import entity.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.HibernateUtil;
import util.JwtUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UserHttpHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("POST")) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/user/signup")) {
                handleSignup(exchange);
            } else if (path.equals("/user/login")) {
                handleLogin(exchange);
            } else if (path.equals("/user/edit")) {
                handleEdit(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleSignup(HttpExchange exchange) throws IOException {
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        User user = new Gson().fromJson(reader, User.class);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            if (isUserTaken(session, user.getUsername()) || isUserTaken(session, user.getPhone())) {
                Response<Void> apiResp = new Response<>("error", "Username already taken");
                String jsonResp = new Gson().toJson(apiResp);
                byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                sendJson(exchange, 400, apiResp);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            session.save(user);
//            Profile profile = new Profile(user.getUsername(), null, user);
//            session.save(profile);

            tx.commit();

            Map<String, Object> data = Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername()
            );
            Response<Map<String, Object>> resp =
                    new Response<>("success", "User created successfully", data);
            sendJson(exchange, 201, resp);

        } catch (Exception e) {
            e.printStackTrace();
            Response<Void> resp = new Response<>("error", "Failed to create user");
            sendJson(exchange, 500, resp);
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {

        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        User loginUser = new Gson().fromJson(reader, User.class);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = getUserByUsername(session, loginUser.getUsername());

            if (user == null || !user.getPassword().equals(loginUser.getPassword())) {
                Response<Void> apiResp = new Response<>("error", "Invalid username or password");
                String jsonResp = new Gson().toJson(apiResp);
                byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(401, bytes.length);  // 401 Unauthorized
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            String token = generateJwtToken(user);
            Map<String, String> data = Map.of(
                    "token", token,
                    "userId", String.valueOf(user.getId()),
                    "role", user.getRole().name()
            );
            Response<Map<String, String>> resp =
                    new Response<>("success", "Login successful", data);
            sendJson(exchange, 200, resp);

        } catch (Exception e) {
            e.printStackTrace();
            Response<Void> resp = new Response<>("error", "Failed to login");
            sendJson(exchange, 500, resp);
        }
    }

    private void handleEdit(HttpExchange exchange) throws IOException {

        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Response<Void> resp = new Response<>("error", "Missing or invalid Authorization header");
            sendJson(exchange, 401, resp);
            return;
        }

        String token = authHeader.substring(7);
        String username = JwtUtil.validateToken(token);
        if (username == null) {
            Response<Void> resp = new Response<>("error", "Invalid username or password");
            sendJson(exchange, 401, resp);
            return;
        }

        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        EditUserRequest req = new Gson().fromJson(reader, EditUserRequest.class);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            User user = getUserByUsername(session, username);
            if (user == null) {
                tx.rollback();
                Response<Void> resp = new Response<>("error", "User not found");
                sendJson(exchange, 404, resp);
                return;
            }

            if (req.getPhone() != null) user.setPhone(req.getPhone());
            if (req.getEmail() != null) user.setEmail(req.getEmail());
            if (req.getAddress() != null) user.setAddress(req.getAddress());
            if (req.getProfilePicture() != null) user.setProfilePicture(req.getProfilePicture());
            if (req.getWallet() != null) user.setWallet(req.getWallet());
            session.update(user);
            tx.commit();
            Response<Void> resp = new Response<>("success", "User updated successfully");
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            Response<Void> resp = new Response<>("error", "Failed to update user");
            sendJson(exchange, 500, resp);
        }
    }

    private <T> void sendJson(HttpExchange exchange, int statusCode, Response<T> resp) throws IOException {
        String json = new Gson().toJson(resp);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isUserTaken(Session session, String input) {
        User user1 = session.createQuery("from User where username = :input", User.class)
                .setParameter("username", input)
                .uniqueResult();
        User user2 = session.createQuery("from User where phone = :input", User.class)
                .setParameter("phone", input)
                .uniqueResult();
        return user1 != null || user2 != null;
    }

    private User getUserByUsername(Session session, String username) {
        return session.createQuery("from User where username = :username", User.class)
                .setParameter("username", username)
                .uniqueResult();
    }

    private String generateJwtToken(User user) {
        return JwtUtil.generateToken(user.getUsername());
    }
}

