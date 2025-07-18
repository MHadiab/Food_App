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
import org.hibernate.query.Query;
import response.ErrorResponse;
import response.MessageResponse;
import response.TransactionResponse;
import util.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OrderHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(java.time.LocalDateTime.class, new LocalDateTimeAdapter())
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
            if ("GET".equalsIgnoreCase(method) && path.equals("/transactions")) {
                handleGetTransactions(ex,token);
            } else if ("POST".equalsIgnoreCase(method) && path.equals("/wallet/top-up")) {
                handleWalletTopUp(ex,token);
            } else if ("PUT".equalsIgnoreCase(method) && path.equals("/payment/online")) {
                handleOnlinePayment(ex,token);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleOnlinePayment(HttpExchange ex, String token) throws IOException {
        System.out.println("online payment");
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex,"BUYER",token)) return;
        PaymentRequest req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), UTF_8), PaymentRequest.class);
        if(req == null){
            JsonHelper.sendJson(ex, 401, new ErrorResponse("invalid request"));
            return;
        }
        if(req.getOrder_id() == null){
            JsonHelper.sendJson(ex, 401, new ErrorResponse("invalid order_id"));
            return;
        }
        if(req.getMethod()==null){
            JsonHelper.sendJson(ex, 401, new ErrorResponse("invalid method"));
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, req.getOrder_id());
            if (order == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid order_id"));
                return;
            }
            User user = order.getUser();
            Query<Transaction> existing = session.createQuery(
                    "FROM Transaction t WHERE t.order.id = :orderId AND t.status = :success",
                    Transaction.class
            );
            existing.setParameter("orderId", order.getId());
            existing.setParameter("success", TransactionStatus.SUCCESS);
            if (!existing.list().isEmpty()) {
                JsonHelper.sendJson(ex, 409, new ErrorResponse("Payment already processed for this order"));
                return;
            }

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
            tr.setOrder(order);
            tr.setAmount(order.getPayPrice());
            tr.setUser(user);
            tr.setDate(java.time.LocalDateTime.now());
            session.persist(tr);
            tx.commit();
            JsonHelper.sendJson(ex, 200, tr);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleWalletTopUp(HttpExchange ex, String token) throws IOException {

        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex,"BUYER",token)) return;
        Long UserId = Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
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


            Transaction tr = new Transaction();
            tr.setMethod(TransactionType.ONLINE);
            tr.setStatus(TransactionStatus.SUCCESS);
            tr.setAmount(req.getAmount());
            tr.setOrder(null);
            tr.setUser(user);
            tr.setDate(java.time.LocalDateTime.now());
            session.persist(tr);







            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Wallet topped up successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }

    }

    private void handleGetTransactions(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex,"BUYER",token)) return;
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
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server Error"));
        }
    }
}
