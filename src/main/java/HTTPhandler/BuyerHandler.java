package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.CreateOrderRequest;
import dto.PaymentRequest;
import entity.*;
import io.jsonwebtoken.io.IOException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.ErrorResponse;
import response.MessageResponse;
import response.OrderResponse;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static util.splitQuery.splitQuery;

public class BuyerHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException, java.io.IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if ("POST".equalsIgnoreCase(method) && path.equals("/orders")) {
            handleCreateOrder(ex); return;
        }
        if("GET".equalsIgnoreCase(method) && path.matches("^/orders/\\d+$")) {
            handleGetOrderDetail(ex); return;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/orders/history")) {
            handleOrderHistory(ex); return;
        }
    }

    private void handleOrderHistory(HttpExchange ex) throws java.io.IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can view order history"));
            return;
        }
        String userIdStr = JwtUtil.getUserIdFromToken(token);
        if (userIdStr == null) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid or expired token"));
            return;
        }
        Long userId = Long.valueOf(userIdStr);
        String queryStr = ex.getRequestURI().getQuery();
        String searchParam = null;
        String vendorParam = null;
        if (queryStr != null && !queryStr.isEmpty()) {
            Map<String, String> params = splitQuery(queryStr);
            searchParam = params.get("search");
            vendorParam = params.get("vendor");
        }
        StringBuilder hql = new StringBuilder("FROM Order o WHERE o.user.user_id = :userId");
        if (searchParam != null && !searchParam.isBlank()) {
            hql.append(" AND (cast(o.id as string) LIKE :search OR o.deliveryAddress LIKE :search)");
        }
        if (vendorParam != null && !vendorParam.isBlank()) {
            hql.append(" AND o.restaurant.id = :vendorId");
        }
        hql.append(" ORDER BY o.createdAt DESC");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Order> query = session.createQuery(hql.toString(), Order.class);
            query.setParameter("userId", userId);
            if (searchParam != null && !searchParam.isBlank()) {
                query.setParameter("search", "%" + searchParam + "%");
            }
            if (vendorParam != null && !vendorParam.isBlank()) {
                try {
                    Integer vendorId = Integer.valueOf(vendorParam);
                    query.setParameter("vendorId", vendorId);
                } catch (NumberFormatException e) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid `vendor` parameter"));
                    return;
                }
            }

            List<Order> orders = query.list();
            List<OrderResponse> respList = new ArrayList<>();
            for (Order o : orders) {
                respList.add(new OrderResponse(o));
            }
            JsonHelper.sendJson(ex, 200, respList);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, Map.of("error", "Internal server error"));
        }
    }

    private void handleGetOrderDetail(HttpExchange ex) throws java.io.IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid id"));
            return;
        }
        int orderId;
        try {
            orderId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid id"));
            return;
        }
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, orderId);
            OrderResponse resp = new OrderResponse(order);
            JsonHelper.sendJson(ex, 200, resp);
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex,500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleCreateOrder(HttpExchange ex) throws java.io.IOException {
        String auth=ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        CreateOrderRequest req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), UTF_8), CreateOrderRequest.class);
        if(req.getVendor_id()==null){
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid vendor_id"));return;
        }
        if(req.getItems().isEmpty()){
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid items"));return;
        }
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            Restaurant restaurant=session.get(Restaurant.class,session.get(Restaurant.class,req.getVendor_id()));
            if(restaurant==null){
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid vendor_id"));return;
            }
            User user = session.get(User.class,JwtUtil.getUserIdFromToken(token));
            org.hibernate.Transaction tx = session.beginTransaction();
            Order order = new Order();
                order.setDeliveryAddress(user.getAddress());
                order.setUser(user);
                order.setRestaurant(restaurant);
                List<OrderItem> orderItems = req.getItems()
                        .stream()
                        .map(dto -> new OrderItem(
                                dto.getItem_id(),
                                dto.getQuantity()
                        ))
                        .collect(Collectors.toList());
                order.setItems(orderItems);
                order.setRawPrice(0);
                order.setTaxFee(0);
                order.setAdditionalFee(0);
                order.setCourierFee(0);
                order.setPayPrice(0);   //این قسمت بعدا باید تکمیل شود
                order.setCreatedAt(LocalDateTime.now());
        }
    }
}
