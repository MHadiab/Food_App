package HTTPhandler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import entity.Transaction;
import org.hibernate.Session;
import response.MessageResponse;
import response.TransactionResponse;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OrderHandler implements HttpHandler {
    private static final Gson gson = new Gson();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();
        String method=ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) && path.equals("/transactions")){
            handleGetTransactions(ex);
        } else if ("POST".equalsIgnoreCase(method) && path.equals("/wallet/top-up")) {
            handleWalletTopUp(ex);
        } else if ("PUT".equalsIgnoreCase(method) && path.equals("/payment/online")) {
            handleOnlinePayment(ex);
        }else {
            ex.sendResponseHeaders(404, -1);
        }
    }
    private void handleOnlinePayment(HttpExchange ex) {

    }

    private void handleWalletTopUp(HttpExchange ex) throws IOException {
        String auth=ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) {
            JsonHelper.sendJson(ex,401,new MessageResponse("Unauthorized"));
            return;
        }
        String token=auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            JsonHelper.sendJson(ex,400,new MessageResponse("Incvalid Input"));
            return;
        }

    }

    private void handleGetTransactions(HttpExchange ex) throws IOException {
        String auth=ex.getRequestHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")){
            JsonHelper.sendJson(ex,401,new MessageResponse("Unauthorized"));
            return;
        }
        String token=auth.substring(7);
        if (!JwtUtil.validateToken(token)){
            JsonHelper.sendJson(ex,401,new MessageResponse("Invalid or expired token"));
            return;
        }
        Long userId = Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
        try(Session session= HibernateUtil.getSessionFactory().openSession()){
            List<Transaction> txs = session.createQuery(
                            "from Transaction t where t.user.id = :uid order by t.date desc",Transaction.class)
                    .setParameter("uid", userId)
                    .list();
            List<TransactionResponse> resp =txs.stream()
                    .map(TransactionResponse::new)
                    .collect(Collectors.toList());


            JsonHelper.sendJson(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error"));
        }
    }
}
