package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.RestaurantChangeStatus;
import entity.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.*;
import util.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dto.RestaurantRequest;

public class RestaurantHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            String auth;
            try {
                auth = ex.getRequestHeaders().getFirst("Authorization");
                if(ErrorHandler.AuthorizationError(ex) || auth == null) throw new Exception("Authorization Error");
            } catch (Exception e) {
                JsonHelper.sendJson(ex, 401, new ErrorResponse("Unauthorized request"));
                return;
            }
            String token = auth.substring(7);
            if ("/restaurants".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleCreateRestaurant(ex, token);
                return;
            }
            if ("/restaurants/mine".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleGetMyRestaurants(ex, token);
                return;
            }
            if (path.matches("/restaurants/\\d+")) {
                if ("PUT".equalsIgnoreCase(method)) {
                    handleUpdateRestaurant(ex, token);
                } else {
                    ex.sendResponseHeaders(405, -1);
                }
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches("^/restaurants/\\d+/orders$")) {
                String[] parts = path.split("/");
                if (parts.length != 4) {
                    JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                    return;
                }
                String idStr = parts[2];
                long vendor_id = Long.parseLong(idStr);
                handleGetOrders(ex, vendor_id, token);
                return;
            }
            if ("PATCH".equalsIgnoreCase(method) && path.matches("^/restaurants/orders/\\d+$")) {
                System.out.println("hi");
                String[] parts = path.split("/");
                if (parts.length != 4) {
                    JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                    return;
                }
                String idStr = parts[3];
                long vendor_id = Long.parseLong(idStr);
                handleOrderStatus(ex, vendor_id, token);
                return;

            }
            ex.sendResponseHeaders(404, -1);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error")); // مشکل سرور
        }
    }

    private void handleOrderStatus(HttpExchange ex, long orderId, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "seller", token)) return;
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid orderId"));
            return;
        }
        RestaurantChangeStatus req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantChangeStatus.class
        );
        if (req.getStatus() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status"));
            return;
        }
        OrderStatus requestedStatus;
        try {
            requestedStatus = OrderStatus.valueOf(req.getStatus().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status value"));
            return;
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            OrderStatus statusEnum;
            try {
                statusEnum = OrderStatus.valueOf(req.getStatus().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status value: " + req.getStatus()));
                return;
            }
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, orderId);
            if (order == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Order not found"));
                return;
            }
            OrderStatus current = order.getStatus();
            boolean validTransition = false;
            switch (current) {
                case SUBMITTED:
                    if (requestedStatus == OrderStatus.UNPAID_AND_CANCELLED ||
                            requestedStatus == OrderStatus.WAITING_VENDOR) {
                        validTransition = true;
                    }
                    break;
                case WAITING_VENDOR:
                    if (requestedStatus == OrderStatus.FINDING_COURIER ||
                            requestedStatus == OrderStatus.CANCELLED) {
                        validTransition = true;
                    }
                    break;
                default:
                    validTransition = false;
            }
            if (!validTransition) {
                JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
                return;
            }
            order.setUpdatedAt(LocalDateTime.now());
            order.setStatus(requestedStatus);
            session.merge(order);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Order status changed successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private void handleGetOrders(HttpExchange ex, long id, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "seller", token)) return;
        String query = ex.getRequestURI().getQuery();
        Map<String, String> params = splitQuery.splitQuery(query);
        String search = params.get("search");
        String status = params.get("status");
        String user = params.get("user");
        String courier = params.get("courier");
        OrderStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = OrderStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status value: " + status));
                return;
            }
        }
        StringBuilder hql = new StringBuilder();
        hql.append("SELECT DISTINCT o")
                .append(" FROM Order o")
                .append(" JOIN o.items oi")
                .append(" JOIN FoodItem fi ON fi.id = oi.itemId")
                .append(" JOIN fi.keywords kw");
        if (user != null && !user.isBlank()) {
            hql.append(" JOIN o.user u");
        }
        hql.append(" WHERE o.restaurant.id = :restaurantId");
        if (search != null && !search.isBlank()) {
            hql.append(" AND (fi.name LIKE :search)");
        }
        if (status != null && !status.isBlank()) {
            hql.append(" AND o.status LIKE :status");
        }
        if (user != null && !user.isBlank()) {
            hql.append(" AND (cast(u.id as string) LIKE :user OR u.full_name LIKE :user)");
        }
        if (courier != null && !courier.isBlank()) {
            hql.append(" AND o.courierId = :courier");
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Restaurant restaurant = session.get(Restaurant.class, id);
            String seller = JwtUtil.getUserIdFromToken(token);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            assert seller != null;
            if (restaurant.getSeller_id() != Long.parseLong(seller)) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Forbidden request"));
                return;
            }
            Query<entity.Order> o = session.createQuery(hql.toString(), entity.Order.class);
            o.setParameter("restaurantId", id);
            if (search != null) o.setParameter("search", search);
            if (status != null) o.setParameter("status", statusEnum);
            if (user != null) o.setParameter("user", user);
            if (courier != null) o.setParameter("courier", courier);
            List<entity.Order> orders = o.list();
            List<OrderResponse> resp = orders.stream().map(OrderResponse::new).toList();
            JsonHelper.sendJson(ex, 200, resp);
        } catch (IOException e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private void handleCreateRestaurant(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "seller", token)) return;
        RestaurantRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantRequest.class
        );
        String MOBILE_REGEX = "^(?:\\+98|0)?9\\d{9}$";
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Bad Request"));
            return;
        }
        if (req.getName() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("invalid name"));
            return;
        }
        if (req.getAddress() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("invalid address"));
            return;
        }
        if (req.getPhone() == null || (!req.getPhone().matches(MOBILE_REGEX) && !req.getPhone().matches("^021\\d{8}$"))) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("invalid phone"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            User user = session.get(User.class, Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token))));
            Restaurant restaurant = new Restaurant();
            restaurant.setName(req.getName());
            restaurant.setAddress(req.getAddress());
            restaurant.setPhone(req.getPhone());
            if (req.getLogoBase64() != null) {
                restaurant.setLogoBase64(req.getLogoBase64());
            }
            restaurant.setTax_fee(req.getTax_fee() != null ? req.getTax_fee() : 0);
            restaurant.setAdditional_fee(req.getAdditional_fee() != null ? req.getAdditional_fee() : 0);
            restaurant.setSeller_id(user.getUser_id());
            session.persist(restaurant);
            tx.commit();
            RestaurantResponse response = new RestaurantResponse(restaurant);
            JsonHelper.sendJson(ex, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private void handleGetMyRestaurants(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "seller", token)) return;
        String userId = JwtUtil.getUserIdFromToken(token);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            assert userId != null;
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
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
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private void handleUpdateRestaurant(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "seller", token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);

        RestaurantRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                RestaurantRequest.class
        );
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            String userId = JwtUtil.getUserIdFromToken(token);
            assert userId != null;
            User user = session.get(User.class, Long.valueOf(userId));
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            Restaurant restaurant = session.get(Restaurant.class, restaurantId);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            if (!user.getUser_id().equals(restaurant.getSeller_id())) {
                JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
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
            JsonHelper.sendJson(ex, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }
}