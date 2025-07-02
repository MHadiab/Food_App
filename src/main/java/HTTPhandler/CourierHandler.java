package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.ChangeStatusRequest;
import entity.Order;
import entity.OrderStatus;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.ErrorResponse;
import response.ErrorResponse;
import response.OrderResponse;
import util.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CourierHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
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
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method) && path.equals("/deliveries/available")) {
                handleGetAvailable(ex,token);
            } else if ("PATCH".equalsIgnoreCase(method) && path.matches("^/deliveries/\\d+$")) {
                handleChangeStatus(ex,token);
            }
//        else if ("GET".equalsIgnoreCase(method) && path.equals("/deliveries/history")) {
//            handleGetHistory(ex);
//        }
            else {
                ex.sendResponseHeaders(404, -1);
            }
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleGetAvailable(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex,"COURIER",token)) return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Order> orders = session.createQuery(
                            "from Order o where o.status = :status",
                            Order.class
                    )
                    .setParameter("status", OrderStatus.FINDING_COURIER)
                    .list();
            List<OrderResponse> resp = orders.stream().map(OrderResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, resp);
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleChangeStatus(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex,"COURIER",token)) return;
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid delivery ID"));
            return;
        }
        int orderId;
        try {
            orderId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid delivery ID"));
            return;
        }

        ChangeStatusRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                ChangeStatusRequest.class
        );
        if (req.getStatus() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Status is required"));
            return;
        }
        OrderStatus requestedStatus;
        try {
            requestedStatus = OrderStatus.valueOf(req.getStatus());
        } catch (IllegalArgumentException e) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status value"));
            return;
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
                case FINDING_COURIER:
                    if (requestedStatus == OrderStatus.ON_THE_WAY) {
                        validTransition = true;
                    }
                    break;
                case ON_THE_WAY:
                    if (requestedStatus == OrderStatus.COMPLETED) {
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
            if (requestedStatus == OrderStatus.FINDING_COURIER) {
                if (order.getCourierId() != null) {
                    JsonHelper.sendJson(ex, 409, new ErrorResponse("Conflict occurred"));
                    return;
                }
                int courierId = Integer.parseInt(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
                order.setCourierId(courierId);
            }
            order.setUpdatedAt(LocalDateTime.now());
            order.setStatus(requestedStatus);
            session.merge(order);
            tx.commit();
            Map<String, Object> data = Map.of(
                    "message", "Changed status successfully",
                    "order", new OrderResponse(order)
            );
            JsonHelper.sendJson(ex, 200, data);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }


    //حذف شده
//    private void handleGetHistory(HttpExchange ex) throws IOException {
//        String auth = ex.getRequestHeaders().getFirst("Authorization");
//        String token = auth.substring(7);
//        if(ErrorHandler.FindError(ex,token)) return;
//        String queryStr = ex.getRequestURI().getQuery();            // e.g. "search=123&vendor=Foo&user=45"
//        Map<String, String> params = splitQuery.splitQuery(queryStr);         // به Map تبدیل می‌کند
//        String search = params.get("search");                      // ممکن است null باشد
//        String vendor = params.get("vendor");                      // ممکن است null باشد
//        String userId = params.get("user");
//
//        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
//            StringBuilder hql = new StringBuilder("from Order o where o.courierId = '' ");
//            if (search != null) hql.append(" and str(o.id) like :search");
//            if (vendor != null) hql.append(" and o.restaurant.name = :vendor");
//            if (userId != null) hql.append(" and o.user.id = :userId");
//            Query<Order> q = session.createQuery(hql.toString(), Order.class);
//            if (search != null) q.setParameter("search", "%" + search + "%");
//            if (vendor != null) q.setParameter("vendor", vendor);
//            if (userId != null) q.setParameter("userId", Long.valueOf(userId));
//
//            List<Order> orders = q.list();
//            List<OrderResponse> resp = orders.stream()
//                    .map(OrderResponse::new)
//                    .collect(Collectors.toList());
//            JsonHelper.sendJson(ex, 200, resp);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server Error"));
//        }
//
//    }
}