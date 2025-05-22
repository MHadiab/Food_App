package HTTPhandler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.ChangeStatusRequest;
import entity.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.MessageResponse;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CourierHandler implements HttpHandler {
    private static final Gson GSON = new Gson();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();
        String method=ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) && path.equals("deliveries/available")){
            handleGetAvailable(ex);
        } else if ("PATCH".equalsIgnoreCase(method) && path.equals("/deliveries/\\w+")) {
            handleChangeStatus(ex);
        } else if ("GET".equalsIgnoreCase(method) && path.equals("/deliveries/history")) {
            handleGetHistory(ex);
        }else {
            ex.sendResponseHeaders(404, -1);
        }
    }
    private void handleGetAvailable(HttpExchange ex) throws IOException {
        String auth=ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex,401,new MessageResponse("Unauthorized"));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token) || !"COURIER".equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden"));
            return;
        }
        try(Session session= HibernateUtil.getSessionFactory().openSession()){
            List<Order> orders=session.createQuery("from Order o where o.status = 'pending'").list();
            List<OrderResponse> resp=orders.stream().map(OrderResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex,200, resp);
        }
    }
    private void handleChangeStatus(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Missing or invalid Authorization header"));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid or expired token"));
            return;
        }
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid delivery ID"));
            return;
        }
        String orderId = parts[2];
        ChangeStatusRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                ChangeStatusRequest.class
        );
        String newStatus = req.getStatus();
        if (newStatus == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Status is required"));
            return;
        }
        try(Session session= HibernateUtil.getSessionFactory().openSession()){
            Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, orderId);
            if(order==null){
                JsonHelper.sendJson(ex, 404, new MessageResponse("Order not found"));
                return;
            }
            String current = order.getStatus();
            boolean valid =
                    ("pending".equals(current)   && "accepted".equals(newStatus)) ||
                            ("accepted".equals(current)  && "received".equals(newStatus)) ||
                            ("received".equals(current)  && "delivered".equals(newStatus));
            if (!valid) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Order status change is not valid"));
                return;
            }
            if ("accepted".equals(newStatus) && order.getCourier() != null) {
                JsonHelper.sendJson(ex, 409, new MessageResponse("Delivery already assigned"));
                return;
            }
            if ("accepted".equals(newStatus)) {
                Long courierId = Long.valueOf(JwtUtil.getUserIdFromToken(token));
                User courier = session.get(User.class, courierId);
                order.setCourier(courier);
            }
            order.setStatus(req.getStatus());
            session.merge(order);
            tx.commit();
            Map<String,Object> data = Map.of(
                    "message", "Changed status successfully",
                    "order", new OrderResponse(order)
            );
            JsonHelper.sendJson(ex, 200, new OrderResponse(order)); //اینجا باید پبام ساکسسفول هم بره اما مطابق senjson نیست
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }
    private void handleGetHistory(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid or expired token"));
            return;
        }
        //بعدا تکمیل میشود

    }
}