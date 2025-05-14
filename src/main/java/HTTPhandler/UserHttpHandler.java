package HTTPhandler;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
                String response="User is already taken!";
                exchange.sendResponseHeaders(400,response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
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
            String response = "User created successfully!";
            exchange.sendResponseHeaders(201, response.getBytes().length); // 201 Created
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            String response = "Failed to create user";
            exchange.sendResponseHeaders(500, response.getBytes().length); // 500 Internal Server Error
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        // Read the request body
        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        User loginUser = new Gson().fromJson(reader, User.class);

        // Validate the user credentials
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = getUserByUsername(session, loginUser.getUsername());

            if (user == null || !user.getPassword().equals(loginUser.getPassword())) {
                String response = "Invalid username or password!";
                exchange.sendResponseHeaders(401, response.getBytes().length); // 401 Unauthorized
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            // Generate JWT token
            String token = generateJwtToken(user);
            // Respond with the JWT token
            String response = "{\"token\":\"" + token + "\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length); // 200 OK
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            String response = "Failed to login";
            exchange.sendResponseHeaders(500, response.getBytes().length); // 500 Internal Server Error
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
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