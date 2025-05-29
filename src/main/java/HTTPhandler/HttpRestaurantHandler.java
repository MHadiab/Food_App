package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import entity.User;
import entity.Role;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.MessageResponse;
import response.RestaurantListResponse;
import response.RestaurantResponse;
import util.HibernateUtil;
import util.JsonHelper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import util.JwtUtil;
import dto.RestaurantRequest;
import entity.Restaurant;
import util.RateLimiter;
import util.ErrorHandler; // اضافه شده

public class HttpRestaurantHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            if ("/restaurants".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleCreateRestaurant(ex);
            } else if ("/restaurants/mine".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleGetMyRestaurants(ex);
            } else if (path.matches("/restaurants/\\d+")) {
                if ("PUT".equalsIgnoreCase(method)) {
                    handleUpdateRestaurant(ex);
                } else {
                    ex.sendResponseHeaders(405, -1); // Method Not Allowed
                }
            } else {
                ex.sendResponseHeaders(404, -1); // Not Found
            }
        } catch (Exception e) {
            e.printStackTrace();
            ex.sendResponseHeaders(500, -1); // Internal Server Error
        }
    }

    private void handleCreateRestaurant(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null; // استخراج توکن

        if (ErrorHandler.FindError(ex, token)) return; // اعتبارسنجی عمومی

        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can create restaurants"));
            return;
        }

        RestaurantRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantRequest.class
        );

        if (req.getName() == null || req.getAddress() == null || req.getPhone() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid `field name`"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            User user = session.get(User.class, Long.valueOf(JwtUtil.getUserIdFromToken(token))); // دریافت کاربر از توکن

            Restaurant restaurant = new Restaurant();
            restaurant.setName(req.getName());
            restaurant.setAddress(req.getAddress());
            restaurant.setPhone(req.getPhone());
            restaurant.setLogoBase64(req.getLogoBase64());
            restaurant.setTax_fee(req.getTax_fee() != null ? req.getTax_fee() : 0); // Default value
            restaurant.setAdditional_fee(req.getAdditional_fee() != null ? req.getAdditional_fee() : 0); // Default value
            restaurant.setSeller_id(user.getUser_id()); // ثبت شناسه فروشنده

            session.persist(restaurant);
            tx.commit();

            RestaurantResponse response = new RestaurantResponse(restaurant);
            JsonHelper.sendJson(ex, 201, response); // Send the created restaurant
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleGetMyRestaurants(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        if (ErrorHandler.FindError(ex, token)) return;

        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can view their restaurants"));
            return;
        }

        String userId = JwtUtil.getUserIdFromToken(token); // Get user ID from token

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found"));
                return;
            }

            List<Restaurant> restaurants = session.createQuery(
                            "from Restaurant where seller_id = :sellerId", Restaurant.class)
                    .setParameter("sellerId", user.getUser_id())
                    .list();

            List<RestaurantResponse> restaurantResponses = restaurants.stream()
                    .map(RestaurantResponse::new)
                    .collect(Collectors.toList());

            RestaurantListResponse response = new RestaurantListResponse(restaurantResponses);
            JsonHelper.sendJson(ex, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }

    private void handleUpdateRestaurant(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        if (ErrorHandler.FindError(ex, token)) return;

        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can update restaurants"));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]); // Assuming /restaurants/{id}

        RestaurantRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantRequest.class
        );

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            String userId = JwtUtil.getUserIdFromToken(token);
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found"));
                return;
            }

            Restaurant restaurant = session.get(Restaurant.class, restaurantId);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found"));
                return;
            }

            // Authorization: Ensure the seller owns the restaurant
            if (!user.getUser_id().equals(restaurant.getSeller_id())) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden request"));
                return;
            }

            if (req.getName() != null) restaurant.setName(req.getName());
            if (req.getAddress() != null) restaurant.setAddress(req.getAddress());
            if (req.getPhone() != null) restaurant.setPhone(req.getPhone());
            if (req.getLogoBase64() != null) restaurant.setLogoBase64(req.getLogoBase64());
            if (req.getTax_fee() != null) restaurant.setTax_fee(req.getTax_fee());
            if (req.getAdditional_fee() != null) restaurant.setAdditional_fee(req.getAdditional_fee());

            session.merge(restaurant);
            tx.commit();

            RestaurantResponse response = new RestaurantResponse(restaurant);
            JsonHelper.sendJson(ex, 200, response); // Send the updated restaurant
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }
}