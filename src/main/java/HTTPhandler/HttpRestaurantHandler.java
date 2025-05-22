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

public class HttpRestaurantHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("/restaurants".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    handleCreateRestaurant(exchange);  //  رستوران جدید
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleGetRestaurants(exchange);    // گرفتن لیست همه رستوران‌ها
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } else if (path.startsWith("/restaurants/")) {
                if ("PUT".equalsIgnoreCase(method)) {
                    handleUpdateRestaurant(exchange);  // بروزرسانی رستوران
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);  // مسیر پیدا نشد
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);  // خطای داخلی سرور
        }
    }

    private void handleCreateRestaurant (HttpExchange exchange) throws IOException {
    }

    private void handleGetRestaurants(HttpExchange exchange) throws IOException {
    }

    private void handleUpdateRestaurant (HttpExchange exchange) throws IOException {}


}
