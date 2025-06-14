package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.CouponRequest;
import dto.UserInfo;
import dto.UserStatusRequest;
import entity.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.*;
import util.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdminHandler implements HttpHandler {
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
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (method.equalsIgnoreCase("GET") && path.equals("/admin/users")) {
                handleGetUsers(ex,token);
                return;
            }
            if ("PATCH".equalsIgnoreCase(method) && path.matches("^/admin/users/\\d+/status$")) {
                String[] parts = path.split("/");
                String idStr = parts[3];
                long userId = Long.parseLong(idStr);
                handleUpdateUserStatus(ex, String.valueOf(userId),token);
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.equals("/admin/orders")) {
                handleGetOrders(ex,token);
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.equals("/admin/transactions")) {
                handleGetTransactions(ex,token);
                return;
            }


            if ("POST".equalsIgnoreCase(method) && "/admin/coupons".equals(path)) {
                handleCreateCoupon(ex,token);
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/admin/coupons".equals(path)) {
                handleListCoupons(ex,token);
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
                handleGetCouponDetails(ex,token);
                return;
            }
            if ("PUT".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
                handleUpdateCoupon(ex,token);
                return;
            }
            if ("DELETE".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
                handleDeleteCoupon(ex,token);
                return;
            }

            ex.sendResponseHeaders(404, -1);
        }catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleGetTransactions(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "ADMIN", token)) return;

        Map<String, String> params = splitQuery.splitQuery(ex.getRequestURI().getQuery());
        String search = params.get("search");
        String user   = params.get("user");
        String method = params.get("method");
        String status = params.get("status");

        StringBuilder hql = new StringBuilder("FROM Transaction t WHERE 1=1");
        if (search != null && !search.isBlank()) {
            hql.append(" AND cast(t.id as string) LIKE :search");
        }
        if (user != null && !user.isBlank()) {
            hql.append(" AND t.user.id = :userId");
        }
        if (method != null && !method.isBlank()) {
            hql.append(" AND t.method = :method");
        }
        if (status != null && !status.isBlank()) {
            hql.append(" AND t.status = :status");
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<entity.Transaction> q = session.createQuery(hql.toString(), entity.Transaction.class);

            if (search != null && !search.isBlank()) {
                q.setParameter("search", "%" + search.trim() + "%");
            }
            if (user != null && !user.isBlank()) {
                q.setParameter("userId", Long.parseLong(user));
            }
            if (method != null && !method.isBlank()) {
                q.setParameter("method", TransactionType.valueOf(method.trim().toUpperCase()));
            }
            if (status != null && !status.isBlank()) {
                q.setParameter("status", TransactionStatus.valueOf(status.trim().toUpperCase()));
            }

            List<entity.Transaction> txs = q.list();
            List<TransactionResponse> resp = txs.stream()
                    .map(TransactionResponse::new)
                    .toList();

            JsonHelper.sendJson(ex, 200, resp);
        } catch (NumberFormatException nfe) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid numeric filter"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }


    private void handleGetOrders(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token) || ErrorHandler.Forbid(ex, "ADMIN", token)) return;

        Map<String, String> params = splitQuery.splitQuery(ex.getRequestURI().getQuery());
        String search   = params.get("search");
        String vendorId = params.get("vendor");
        String courier  = params.get("courier");
        String customer = params.get("customer");
        String status   = params.get("status");

        StringBuilder hql = new StringBuilder("FROM Order o WHERE 1=1");
        if (search != null && !search.isBlank()) {
            hql.append(" AND (cast(o.id as string) LIKE :search")
                    .append(" OR o.deliveryAddress LIKE :search)");
        }
        if (vendorId != null && !vendorId.isBlank()) {
            hql.append(" AND cast(o.restaurant.id as string) LIKE :vendor");
        }
        if (courier != null && !courier.isBlank()) {
            hql.append(" AND cast(o.courierId as string) LIKE :courier");
        }
        if (customer != null && !customer.isBlank()) {
            hql.append(" AND cast(o.user.id as string) LIKE :customer");
        }
        if (status != null && !status.isBlank()) {
            hql.append(" AND cast(o.status as string) LIKE :status");
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<entity.Order> q = session.createQuery(hql.toString(), entity.Order.class);

            if (search != null && !search.isBlank()) {
                q.setParameter("search", "%" + search.trim() + "%");
            }
            if (vendorId != null && !vendorId.isBlank()) {
                q.setParameter("vendor", "%" + vendorId.trim() + "%");
            }
            if (courier != null && !courier.isBlank()) {
                q.setParameter("courier", "%" + courier.trim() + "%");
            }
            if (customer != null && !customer.isBlank()) {
                q.setParameter("customer", "%" + customer.trim() + "%");
            }
            if (status != null && !status.isBlank()) {
                q.setParameter("status", "%" + status.trim().toUpperCase() + "%");
            }

            List<entity.Order> orders = q.list();
            List<OrderResponse> resp = orders.stream()
                    .map(OrderResponse::new)
                    .toList();

            JsonHelper.sendJson(ex, 200, resp);
        } catch (NumberFormatException nfe) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid numeric filter"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleUpdateUserStatus(HttpExchange ex, String path, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        long userId = Long.parseLong(path);
        UserStatusRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                UserStatusRequest.class
        );
        if (req == null || req.getStatus() == null || (!req.getStatus().equalsIgnoreCase(String.valueOf(UserStatus.APPROVED)) && !req.getStatus().equalsIgnoreCase(UserStatus.REJECTED.name()))) {
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

    private void handleGetUsers(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<User> users = session
                    .createQuery("FROM User", User.class)
                    .list();
            List<UserInfo> rep = users.stream().map(UserInfo::new)
                    .toList();
            JsonHelper.sendJson(ex, 200, rep);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }




    private void handleCreateCoupon(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        CouponRequest req = null;
        try {
            req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8), CouponRequest.class);
            if (req == null) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Request body is empty or malformed."));
                return;
            }
        } catch (JsonSyntaxException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON format in request body."));
            return;
        } catch (IOException e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error reading request body."));
            return;
        }

        if (req.getCouponCode() == null || req.getCouponCode().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon_code"));
            return;
        }
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid type"));
            return;
        }
        if (req.getValue() == null || req.getValue() <= 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid value"));
            return;
        }
        if (req.getMinPrice() == null || req.getMinPrice() < 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid min_price"));
            return;
        }
        if (req.getUserCount() == null  || req.getUserCount() < 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid user_count"));
            return;
        }
        if (req.getStartDate() == null || req.getStartDate().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid start_date"));
            return;
        }
        if (req.getEndDate() == null || req.getEndDate().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid end_date"));
            return;
        }



        CouponType couponType;
        LocalDate startDate, endDate;
        try {
            couponType = CouponType.valueOf(req.getType().toUpperCase());
            startDate = LocalDate.parse(req.getStartDate());
            endDate = LocalDate.parse(req.getEndDate());
            if (endDate.isBefore(startDate)) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("End date cannot be before start date."));
                return;
            }

            if (couponType == CouponType.PERCENT && req.getValue() > 100) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid value"));
                return;
            }

        } catch (IllegalArgumentException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon type. Allowed: FIXED, PERCENT."));
            return;
        } catch (DateTimeParseException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid date format. Use YYYY-MM-DD."));
            return;
        }


        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            // بررسی یکتا بودن کد کوپن
            Query<Long> existingCouponQuery = session.createQuery("SELECT count(c.id) FROM Coupon c WHERE c.couponCode = :code", Long.class);
            existingCouponQuery.setParameter("code", req.getCouponCode());
            if (existingCouponQuery.uniqueResult() > 0) {
                JsonHelper.sendJson(ex, 409, new MessageResponse("Conflict: Coupon code already exists."));
                if (tx.isActive()) tx.rollback();
                return;
            }


            Coupon coupon = new Coupon();
            coupon.setCouponCode(req.getCouponCode());
            coupon.setType(couponType);
            coupon.setValue(req.getValue());
            coupon.setMinPrice(req.getMinPrice());
            coupon.setUserCount(req.getUserCount());
            coupon.setStartDate(startDate);
            coupon.setEndDate(endDate);
            coupon.setActive(true); // فیلد اکتیو رو همین طوری گذاشتم میتونیم بعدا برش داریم

            session.persist(coupon);
            tx.commit();
            JsonHelper.sendJson(ex, 201, new CouponResponse(coupon));   // چرا استتوس کد 201 دادن برای این دستور ._.
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while creating coupon."));
        }
    }

    private void handleListCoupons(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Coupon> coupons = session.createQuery("FROM Coupon", Coupon.class).list();
            List<CouponResponse> couponResponses = coupons.stream()
                    .map(CouponResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, couponResponses);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while listing coupons."));
        }
    }

    private void handleGetCouponDetails(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer couponId;
        try {
            if (parts.length > 3) {
                couponId = Integer.parseInt(parts[3]);
            } else {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Coupon ID missing in path."));
                return;
            }
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon ID format."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Coupon coupon = session.get(Coupon.class, couponId);
            if (coupon == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon not found."));
                return;
            }
            JsonHelper.sendJson(ex, 200, new CouponResponse(coupon));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while fetching coupon details."));
        }
    }


    private void handleUpdateCoupon(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer couponId;
        try {
            if (parts.length > 3) {
                couponId = Integer.parseInt(parts[3]);
            } else {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Coupon ID missing in path."));
                return;
            }
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon ID format."));
            return;
        }

        CouponRequest req = null;
        try {
            req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8), CouponRequest.class);
            if (req == null) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Request body is empty or malformed."));
                return;
            }
        } catch (JsonSyntaxException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON format in request body."));
            return;
        } catch (IOException e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error reading request body."));
            return;
        }

        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Coupon coupon = session.get(Coupon.class, couponId);
            if (coupon == null) {  // کوپنی با این ایدی نداشتیم
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon not found."));
                if (tx.isActive()) tx.rollback();
                return;
            }

            if (req.getCouponCode() != null && !req.getCouponCode().trim().isEmpty()) {
                if (!coupon.getCouponCode().equals(req.getCouponCode())) {
                    Query<Long> existingCouponQuery = session.createQuery("SELECT count(c.id) FROM Coupon c WHERE c.couponCode = :code AND c.id != :currentId", Long.class);
                    existingCouponQuery.setParameter("code", req.getCouponCode());
                    existingCouponQuery.setParameter("currentId", couponId);
                    if (existingCouponQuery.uniqueResult() > 0) {
                        JsonHelper.sendJson(ex, 409, new MessageResponse("Conflict: New coupon code already exists."));
                        if (tx.isActive()) tx.rollback();
                        return;
                    }
                }
                coupon.setCouponCode(req.getCouponCode());
            }
            if (req.getType() != null && !req.getType().trim().isEmpty()) {
                try {
                    coupon.setType(CouponType.valueOf(req.getType().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon type for update."));
                    if (tx.isActive()) tx.rollback();
                    return;
                }
            }
            if (req.getValue() != null) coupon.setValue(req.getValue());
            if (req.getMinPrice() != null) coupon.setMinPrice(req.getMinPrice());
            if (req.getUserCount() != null) coupon.setUserCount(req.getUserCount());
            LocalDate newStartDate = null, newEndDate = null;
            if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
                try {
                    newStartDate = LocalDate.parse(req.getStartDate());
                } catch (DateTimeParseException e) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid start date format for update. Use YYYY-MM-DD."));
                    if (tx.isActive()) tx.rollback();
                    return;
                }
            }
            if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
                try {
                    newEndDate = LocalDate.parse(req.getEndDate());
                } catch (DateTimeParseException e) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid end date format for update. Use YYYY-MM-DD."));
                    if (tx.isActive()) tx.rollback();
                    return;
                }
            }

            LocalDate effectiveStartDate = newStartDate != null ? newStartDate : coupon.getStartDate();
            LocalDate effectiveEndDate = newEndDate != null ? newEndDate : coupon.getEndDate();

            if (effectiveEndDate.isBefore(effectiveStartDate)) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("End date cannot be before start date."));
                if (tx.isActive()) tx.rollback();
                return;
            }
            if (newStartDate != null) coupon.setStartDate(newStartDate);
            if (newEndDate != null) coupon.setEndDate(newEndDate);

            Double valueToCheck = req.getValue() != null ? req.getValue() : coupon.getValue();
            CouponType typeToCheck = req.getType() != null ? CouponType.valueOf(req.getType().toUpperCase()) : coupon.getType();
            Integer minPriceToCheck = req.getMinPrice() != null ? req.getMinPrice() : coupon.getMinPrice();
            Integer userCountToCheck = req.getUserCount() != null ? req.getUserCount() : coupon.getUserCount();


            // فیلد های این بخش میتونن دقیق تر هم بررسی شن و توی یه if نباشن
            if (valueToCheck <= 0 || (typeToCheck == CouponType.PERCENT && valueToCheck > 100) || minPriceToCheck < 0 || userCountToCheck < 0) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid numeric values for coupon (value, minPrice, userCount) during update. Percent value should be between 0 and 100."));
                if (tx.isActive()) tx.rollback();
                return;
            }


            session.merge(coupon);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new CouponResponse(coupon));
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while updating coupon."));
        }
    }


    private void handleDeleteCoupon(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex,token) || ErrorHandler.Forbid(ex,"ADMIN",token)) return;

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer couponId;
        try {
            if (parts.length > 3) {
                couponId = Integer.parseInt(parts[3]);
            } else {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Coupon ID missing in path."));
                return;
            }
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid coupon ID format."));
            return;
        }

        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Coupon coupon = session.get(Coupon.class, couponId);
            if (coupon == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon not found."));
                if (tx.isActive()) tx.rollback();
                return;
            }
            session.remove(coupon);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Coupon deleted successfully."));
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while deleting coupon."));
        }
    }
}
