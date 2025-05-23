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

import entity.User;
import util.JwtUtil;
import dto.RestaurantRequest;
import entity.Restaurant;
import util.RateLimiter;
import util.TokenBlacklist;

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
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }

        String token = auth.substring(7);
        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can create restaurants"));
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
            return;
        }

        RestaurantRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantRequest.class
        );

        String clientIp = ex.getRemoteAddress()
                .getAddress()
                .getHostAddress();
        if (RateLimiter.allowRequest(clientIp)) {
            JsonHelper.sendJson(ex, 429, new Error("Too many requests"));
            return;
        }

        if (req.getName() == null || req.getAddress() == null || req.getPhone() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            Restaurant restaurant = new Restaurant();
            restaurant.setName(req.getName());
            restaurant.setAddress(req.getAddress());
            restaurant.setPhone(req.getPhone());
            restaurant.setLogoBase64(req.getLogoBase64());
            restaurant.setTax_fee(req.getTax_fee() != null ? req.getTax_fee() : 0); // Default value
            restaurant.setAdditional_fee(req.getAdditional_fee() != null ? req.getAdditional_fee() : 0); // Default value

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
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }

        String token = auth.substring(7);
        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can view their restaurants"));
            return;
        }

        String userId = JwtUtil.getUserIdFromToken(token); // Get user ID from token

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }

            List<Restaurant> restaurants = session.createQuery(
                            "from Restaurant where seller_id = :sellerId", Restaurant.class) // Assuming you have seller_id in Restaurant
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
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }

        String token = auth.substring(7);
        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only sellers can update restaurants"));
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new Error("Unsupported Media Type"));
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
                JsonHelper.sendJson(ex, 404, new MessageResponse("User not found"));
                return;
            }

            Restaurant restaurant = session.get(Restaurant.class, restaurantId);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Restaurant not found"));
                return;
            }

            // Authorization: Ensure the seller owns the restaurant
            if (!user.getUser_id().equals(restaurant.getSeller_id())) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: You do not own this restaurant"));
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