package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.ChangeStatusRequest;
import entity.Order;
import entity.OrderStatus;
import entity.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.ErrorResponse;
import response.ErrorResponse;
import response.MessageResponse;
import response.OrderResponse;
import util.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CourierHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) && path.equals("/deliveries/available")) {
            handleGetAvailable(ex);
        } else if ("PATCH".equalsIgnoreCase(method) && path.matches("^/deliveries/\\d+$")) {
            handleChangeStatus(ex);
        } else if ("GET".equalsIgnoreCase(method) && path.equals("/deliveries/history")) {
            handleGetHistory(ex);
        } else {
            ex.sendResponseHeaders(404, -1);
        }
    }

    private void handleGetAvailable(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        if (!JwtUtil.validateToken(token) || !"COURIER".equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Order> orders = session.createQuery(
                            "from Order o where o.status = :status",  // HQL با پارامتر
                            Order.class                                // نوع خروجی
                    )
                    .setParameter("status", OrderStatus.PENDING)
                    .list();
            List<OrderResponse> resp = orders.stream().map(OrderResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, resp);
        }
    }

    private void handleChangeStatus(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid delivery ID"));
            return;
        }
        String orderId = parts[2];
        ChangeStatusRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), UTF_8),
                ChangeStatusRequest.class
        );
        String newStatus = String.valueOf(req.getStatus());
        if (newStatus == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Status is required"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, orderId);
            if (order == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Order not found"));
                return;
            }
            String current = String.valueOf(order.getStatus());
            boolean valid =
                    ("pending".equals(current) && "accepted".equals(newStatus)) ||
                            ("accepted".equals(current) && "received".equals(newStatus)) ||
                            ("received".equals(current) && "delivered".equals(newStatus));
            if (!valid) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Order status change is not valid"));
                return;
            }
            if ("accepted".equals(newStatus) && order.getCourierId() != null) {
                JsonHelper.sendJson(ex, 409, new MessageResponse("Delivery already assigned"));
                return;
            }
            if ("accepted".equals(newStatus)) {
                int courierId = Integer.parseInt(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
                User courier = session.get(User.class, courierId);
                order.setCourierId(Math.toIntExact(courier.getUser_id()));
            }
            order.setStatus(req.getStatus());
            session.merge(order);
            tx.commit();
            Map<String, Object> data = Map.of(
                    "message", "Changed status successfully",
                    "order", new OrderResponse(order)
            );
            JsonHelper.sendJson(ex, 200, new OrderResponse(order)); //اینجا باید پبام ساکسسفول هم بره اما مطابق senjson نیست
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server Error"));
        }
    }

    private void handleGetHistory(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        String queryStr = ex.getRequestURI().getQuery();            // e.g. "search=123&vendor=Foo&user=45"
        Map<String, String> params = splitQuery.splitQuery(queryStr);         // به Map تبدیل می‌کند
        String search = params.get("search");                      // ممکن است null باشد
        String vendor = params.get("vendor");                      // ممکن است null باشد
        String userId = params.get("user");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder("from Order o where o.status != 'pending'");
            if (search != null) hql.append(" and str(o.id) like :search");
            if (vendor != null) hql.append(" and o.restaurant.name = :vendor");
            if (userId != null) hql.append(" and o.user.id = :userId");
            Query<Order> q = session.createQuery(hql.toString(), Order.class);
            if (search != null) q.setParameter("search", "%" + search + "%");
            if (vendor != null) q.setParameter("vendor", vendor);
            if (userId != null) q.setParameter("userId", Long.valueOf(userId));

            List<Order> orders = q.list();
            List<OrderResponse> resp = orders.stream()
                    .map(OrderResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, resp);

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server Error"));
        }

    }
}