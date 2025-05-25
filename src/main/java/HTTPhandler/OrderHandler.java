package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.PaymentRequest;
import dto.WalletTopUpRequest;
import entity.*;
import org.hibernate.Session;
import response.ErrorResponse;
import response.ErrorResponse;
import response.MessageResponse;
import response.TransactionResponse;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;
import util.RateLimiter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OrderHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        System.out.println(path + " " + method);
        if ("GET".equalsIgnoreCase(method) && path.equals("/transactions")) {
            handleGetTransactions(ex);
        } else if ("POST".equalsIgnoreCase(method) && path.equals("/wallet/top-up")) {
            handleWalletTopUp(ex);
        } else if ("PUT".equalsIgnoreCase(method) && path.equals("/payment/online")) {
            handleOnlinePayment(ex);
        } else {
            ex.sendResponseHeaders(404, -1);
        }
    }

    private void handleOnlinePayment(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }
        String token = auth.substring(7);
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new ErrorResponse("Unsupported Media Type"));
            return;
        }

        String userKey = JwtUtil.getUserIdFromToken(token);
        if (!RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new ErrorResponse("Too many requests"));
            return;
        }
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid Input"));
            return;
        }
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        PaymentRequest req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), UTF_8), PaymentRequest.class);
        if (req.getOrder_id() == null || req.getMethod() == null) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, req.getOrder_id());
            if (order == null) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input"));
                return;
            }
            User user = order.getUser();
            if (req.getMethod().equalsIgnoreCase("wallet")) {
                if (user.getBalance() < order.getPayPrice()) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Insufficient balance"));
                    return;
                }
            }
            order.setStatus(OrderStatus.WAITING_VENDOR);
            session.merge(order);

            Transaction tr = new Transaction();
            if (req.getMethod().equalsIgnoreCase("wallet")) tr.setMethod(TransactionType.WALLET);
            else tr.setMethod(TransactionType.ONLINE);
            tr.setStatus(TransactionStatus.SUCCESS);
            tr.setAmount(order.getPayPrice());
            tr.setUser(user);
            tr.setDate(java.time.LocalDateTime.now());
            session.persist(tr);
            tx.commit();
            JsonHelper.sendJson(ex, 200, tr);
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal Server Error"));
        }
    }

    private void handleWalletTopUp(HttpExchange ex) throws IOException {

        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }
        String token = auth.substring(7);
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new ErrorResponse("Unsupported Media Type"));
            return;
        }
        //اینجا یک باگ یافت شد
        String userKey = JwtUtil.getUserIdFromToken(token);
        if (!RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new ErrorResponse("Too many requests"));
            return;
        }

        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid Input"));
            return;
        }

        Long UserId = Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        WalletTopUpRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), UTF_8),
                WalletTopUpRequest.class
        );
        if (req.getAmount() <= 0) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
            return;
        }


        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            User user = session.get(User.class, UserId);
            if (user == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("User not found"));
            }
            assert user != null;
            Double current = user.getBalance();
            if (current == null) {
                current = 0.0;
            }
            user.setBalance(current + req.getAmount());
            user.setBalance(req.getAmount() + user.getBalance());
            session.merge(user);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Wallet topped up successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }

    }

    private void handleGetTransactions(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Unauthorized"));
            return;
        }

        String token = auth.substring(7);
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            JsonHelper.sendJson(ex, 415, new ErrorResponse("Unsupported Media Type"));
            return;
        }
        System.out.println("hi");

        String userKey = JwtUtil.getUserIdFromToken(token);
        if (!RateLimiter.allowRequest(userKey)) {
            JsonHelper.sendJson(ex, 429, new ErrorResponse("Too many requests"));
            return;
        }

        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex, 401, new MessageResponse("Invalid or expired token"));
            return;
        }
        Long userId = Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Transaction> txs = session.createQuery(
                            "from transactions t where t.user.id = :uid order by t.date desc", Transaction.class)
                    .setParameter("uid", userId)
                    .list();
            List<TransactionResponse> resp = txs.stream()
                    .map(TransactionResponse::new)
                    .collect(Collectors.toList());


            JsonHelper.sendJson(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server Error"));
        }
    }
}
