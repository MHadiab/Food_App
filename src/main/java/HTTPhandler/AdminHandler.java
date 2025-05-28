package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.RegisterRequest;
import dto.UserInfo;
import dto.UserStatusRequest;
import entity.*;
import io.jsonwebtoken.io.IOException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.ErrorResponse;
import response.MessageResponse;
import response.OrderResponse;
import response.TransactionResponse;
import util.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdminHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    @Override
    public void handle(HttpExchange ex) throws IOException, java.io.IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if (method.equalsIgnoreCase("GET") && path.equals("/admin/users")) {
            handleGetUsers(ex); return;
        }
        if ("PATCH".equalsIgnoreCase(method) && path.matches("^/admin/users/\\d+/status$")) {
            String[] parts = path.split("/");
            String idStr = parts[3];
            long userId = Long.parseLong(idStr);
            handleUpdateUserStatus(ex, String.valueOf(userId)); return;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/admin/orders")) {
            handleGetOrders(ex); return;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/admin/transactions")) {
            handleGetTransactions(ex); return;
        }
        ex.sendResponseHeaders(404, -1);
    }

    private void handleGetTransactions(HttpExchange ex) throws java.io.IOException {
        String query = ex.getRequestURI().getQuery();
        Map<String, String> params = splitQuery.splitQuery(query);
        String search = params.get("search");
        String user = params.get("user");
        String method = params.get("method");
        String status=params.get("status");
        StringBuilder hql = new StringBuilder("FROM transactions t WHERE 1=1");
        if (search != null) hql.append(" AND cast(t.id as string) LIKE :search");
        if (user != null) hql.append(" AND t.user.id = :userId");
        if (method != null) hql.append(" AND t.method = :method");
        if (status != null) hql.append(" AND t.status = :status");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<entity.Transaction> q = session.createQuery(hql.toString(), entity.Transaction.class);
            if (search != null) q.setParameter("search", "%" + search + "%");
            if (user != null) q.setParameter("userId", Long.parseLong(user));
            if (method != null) q.setParameter("method", TransactionType.valueOf(method.toUpperCase()));
            if (status != null) q.setParameter("status", TransactionStatus.valueOf(status.toUpperCase()));
            List<entity.Transaction> txs = q.list();
            List<TransactionResponse> resp = txs.stream()
                    .map(TransactionResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, Map.of("error", "Internal server error"));
        }
    }

    private void handleGetOrders(HttpExchange ex) throws java.io.IOException {
        String query=ex.getRequestURI().getQuery();
        Map<String,String> params = splitQuery.splitQuery(query);
        String search = params.get("search");
        String vendor = params.get("vendor");
        String courier = params.get("courier");
        String customer = params.get("customer");
        String status = params.get("status");

        StringBuilder hql = new StringBuilder("From order o WHERE 1=1");
        if (search != null) hql.append(" AND (cast(o.id as string) LIKE :search OR o.deliveryAddress LIKE :search)");
        if (vendor != null)   hql.append(" AND o.restaurant.id = :vendor");
        if (courier != null)  hql.append(" AND o.courierId = :courier");
        if (customer != null)   hql.append(" AND o.user.id = :customer");
        if (status != null)     hql.append(" AND o.status = :status");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Order> queryObj = session.createQuery(hql.toString(), Order.class);
            if (search != null)     queryObj.setParameter("search", "%" + search + "%");
            if (vendor != null)   queryObj.setParameter("vendor", Integer.parseInt(vendor));
            if (courier != null)  queryObj.setParameter("courier", Integer.parseInt(courier));
            if (customer != null)   queryObj.setParameter("customer", Integer.parseInt(customer));
            if (status != null)     queryObj.setParameter("status", OrderStatus.valueOf(status.toUpperCase()));

            List<Order> orders = queryObj.list();

            List<OrderResponse> resp = orders.stream()
                    .map(OrderResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, resp);

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleUpdateUserStatus(HttpExchange ex, String path) throws IOException, java.io.IOException {

        long userId = Long.parseLong(path.split("/")[3]);
         UserStatusRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                 UserStatusRequest.class
        );
        if (req == null || req.getStatus()==null || (!req.getStatus().equalsIgnoreCase(String.valueOf(UserStatus.APPROVED))&& !req.getStatus().equalsIgnoreCase(UserStatus.REJECTED.name()))) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status"));
            return;
        }
        String newStatus = req.getStatus().toUpperCase();
        UserStatus us;
        try {
            us = UserStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid status"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            User user = session.get(User.class, userId);
            if (user == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            user.setStatus(us);
            session.merge(user);
            tx.commit();
        } catch (Exception exn) {
            exn.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
            return;
        }
        JsonHelper.sendJson(ex, 200, new MessageResponse("Status updated"));
    }

    private void handleGetUsers(HttpExchange ex) throws java.io.IOException {
        String auth=ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if(ErrorHandler.FindError(ex,token)) return;
        if(!Objects.equals(JwtUtil.getRoleFromToken(token), Role.ADMIN.name())) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
            return;
        }
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<User> users = session
                    .createQuery("FROM User", User.class)
                    .list();
            List<UserInfo> rep =users.stream().map(UserInfo :: new)
                    .toList();
            JsonHelper.sendJson(ex, 200, rep);
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }
}
