package HTTPhandler;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
                }else if (path.equals("/user/update")) {
                    //--
                }else{
                    exchange.sendResponseHeaders(404, -1);
                }
            }else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

    private void handleSignup(HttpExchange exchange) throws IOException {
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(),StandardCharsets.UTF_8);
        User user = new Gson().fromJson(reader, User.class);

        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            if (isUserTaken(session,user.getUsername()) || isUserTaken(session,user.getPhone())){
                Response<Void> apiResp = new Response<>("error", "Username already taken");
                String jsonResp = new Gson().toJson(apiResp);
                byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            // Save the new user
            session.save(user);
//            Profile profile = new Profile(user.getUsername(), null, user);
//            session.save(profile);

            // Commit the transaction
            tx.commit();
            // Respond with success
// پس از commit
            Response<Map<String, Object>> apiResp =
                    new Response<>("success", "User created successfully!",
                            Map.of("userId", user.getId(), "username", user.getUsername()));
            String jsonResp = new Gson().toJson(apiResp);
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(201, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

        } catch (Exception e) {
            Response<Void> apiResp = new Response<>("error","fail to create user");
            String jsonResp = new Gson().toJson(apiResp);
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        // تنظیم نوع محتوا
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        // خواندن بدنه‌ی درخواست و دِسیریالایز به User
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        User loginUser = new Gson().fromJson(reader, User.class);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // جستجوی کاربر بر اساس یوزرنیم
            User user = getUserByUsername(session, loginUser.getUsername());

            // اعتبارسنجی
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

            // تولید توکن JWT
            String token = generateJwtToken(user);

            // ساخت پاسخ موفق
            Map<String, String> data = Map.of(
                    "token", token,
                    "userId", String.valueOf(user.getId()),
                    "role", user.getRole().name()
            );
            Response<Map<String, String>> apiResp =
                    new Response<>("success", "Login successful", data);
            String jsonResp = new Gson().toJson(apiResp);
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(200, bytes.length);  // 200 OK
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Response<Void> apiResp = new Response<>("error", "Failed to login");
            String jsonResp = new Gson().toJson(apiResp);
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(500, bytes.length);  // 500 Internal Server Error
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
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