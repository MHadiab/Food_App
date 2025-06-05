package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.CouponRequest;
import dto.RegisterRequest;
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
    public void handle(HttpExchange ex) throws IOException {   //  تنها تغیری که توی کد های هادی دادم عوض کردن پرتاب اکسپشن برای متد ها بود
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

        // 5 دستور آخر ادمین برای من
        if ("POST".equalsIgnoreCase(method) && "/admin/coupons".equals(path)) {
            handleCreateCoupon(ex); return;
        }
        if ("GET".equalsIgnoreCase(method) && "/admin/coupons".equals(path)) {
            handleListCoupons(ex); return;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
            handleGetCouponDetails(ex); return;
        }
        if ("PUT".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
            handleUpdateCoupon(ex); return;
        }
        if ("DELETE".equalsIgnoreCase(method) && path.matches("/admin/coupons/\\d+")) {
            handleDeleteCoupon(ex); return;
        }

        ex.sendResponseHeaders(404, -1);
    }

    private void handleGetTransactions(HttpExchange ex) throws IOException {
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

    private void handleGetOrders(HttpExchange ex) throws IOException {
        String query=ex.getRequestURI().getQuery();
        Map<String,String> params = splitQuery.splitQuery(query);
        String search = params.get("search");
        String vendor = params.get("vendor");
        String courier = params.get("courier");
        String customer = params.get("customer");
        String status = params.get("status");

        StringBuilder hql = new StringBuilder("From Order o WHERE 1=1");
        if (search != null) hql.append(" AND (cast(o.id as string) LIKE :search OR o.deliveryAddress LIKE :search)");
        if (vendor != null)   hql.append(" AND o.restaurant.id = :vendor");
        if (courier != null)  hql.append(" AND o.courierId = :courier");
        if (customer != null)   hql.append(" AND o.user.id = :customer");
        if (status != null)     hql.append(" AND o.status = :status");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<entity.Order> queryObj = session.createQuery(hql.toString(), entity.Order.class);
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

    private void handleUpdateUserStatus(HttpExchange ex, String path) throws IOException {
        long userId = Long.parseLong(path);
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

    private void handleGetUsers(HttpExchange ex) throws IOException {
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

    private void handleCreateCoupon(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        if (!Role.ADMIN.name().equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Admin access required."));
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

        // اعتبارسنجی فیلدهای الزامی از CouponRequest
        if (req.getCouponCode() == null || req.getCouponCode().trim().isEmpty() ||
                req.getType() == null || req.getType().trim().isEmpty() ||
                req.getValue() == null ||
                req.getMinPrice() == null ||
                req.getUserCount() == null ||
                req.getStartDate() == null || req.getStartDate().trim().isEmpty() ||
                req.getEndDate() == null || req.getEndDate().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Missing required fields for coupon creation."));
            return;
        }


        // بررسی شرایط منطقی برای هر فیلد
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

            //  این بخش رو باید برای هر فیلد بعدا جدا کنیم
            if (req.getValue() <= 0 || (couponType == CouponType.PERCENT && req.getValue() > 100) || req.getMinPrice() < 0 || req.getUserCount() < 0) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid numeric values for coupon (value, minPrice, userCount). Percent value should be between 0 and 100."));
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
                if(tx.isActive()) tx.rollback();
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

    private void handleListCoupons(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        if (!Role.ADMIN.name().equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Admin access required."));
            return;
        }

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

    private void handleGetCouponDetails(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        if (!Role.ADMIN.name().equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Admin access required."));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer couponId;
        try {
            if (parts.length > 3) { // بررسی دارا بودن شرایط دستور های داده شده
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
            if (coupon == null) {  //  کوپنی با این ایدی نداشتیم
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon not found."));
                return;
            }
            JsonHelper.sendJson(ex, 200, new CouponResponse(coupon));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while fetching coupon details."));
        }
    }


    private void handleUpdateCoupon(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        if (!Role.ADMIN.name().equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Admin access required."));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer couponId;
        try {
            if (parts.length > 3) {  // بررسی دارا بودن شرایط دستور های داده شده
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
            if (req == null) { // بدنه درخواست نباید خالی باشد برای آپدیت
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
                if(tx.isActive()) tx.rollback();
                return;
            }

            // اعمال تغیرات داده شده
            if (req.getCouponCode() != null && !req.getCouponCode().trim().isEmpty()) {
                // بررسی یکتا بودن کد جدید اگر تغییر کرده است
                if (!coupon.getCouponCode().equals(req.getCouponCode())) {
                    Query<Long> existingCouponQuery = session.createQuery("SELECT count(c.id) FROM Coupon c WHERE c.couponCode = :code AND c.id != :currentId", Long.class);
                    existingCouponQuery.setParameter("code", req.getCouponCode());
                    existingCouponQuery.setParameter("currentId", couponId);
                    if (existingCouponQuery.uniqueResult() > 0) {
                        JsonHelper.sendJson(ex, 409, new MessageResponse("Conflict: New coupon code already exists."));
                        if(tx.isActive()) tx.rollback();
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
                    if(tx.isActive()) tx.rollback();
                    return;
                }
            }
            if (req.getValue() != null) coupon.setValue(req.getValue());
            if (req.getMinPrice() != null) coupon.setMinPrice(req.getMinPrice());
            if (req.getUserCount() != null) coupon.setUserCount(req.getUserCount());
            LocalDate newStartDate = null, newEndDate = null;
            if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
                try { newStartDate = LocalDate.parse(req.getStartDate()); }
                catch (DateTimeParseException e) { JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid start date format for update. Use YYYY-MM-DD.")); if(tx.isActive()) tx.rollback(); return; }
            }
            if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
                try { newEndDate = LocalDate.parse(req.getEndDate()); }
                catch (DateTimeParseException e) { JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid end date format for update. Use YYYY-MM-DD.")); if(tx.isActive()) tx.rollback(); return; }
            }

            LocalDate effectiveStartDate = newStartDate != null ? newStartDate : coupon.getStartDate();
            LocalDate effectiveEndDate = newEndDate != null ? newEndDate : coupon.getEndDate();

            if (effectiveEndDate.isBefore(effectiveStartDate)) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("End date cannot be before start date."));
                if(tx.isActive()) tx.rollback();
                return;
            }
            if (newStartDate != null) coupon.setStartDate(newStartDate);
            if (newEndDate != null) coupon.setEndDate(newEndDate);

            // بررسی مقدار های عددی
            Double valueToCheck = req.getValue() != null ? req.getValue() : coupon.getValue();
            CouponType typeToCheck = req.getType() != null ? CouponType.valueOf(req.getType().toUpperCase()) : coupon.getType();
            Integer minPriceToCheck = req.getMinPrice() != null ? req.getMinPrice() : coupon.getMinPrice();
            Integer userCountToCheck = req.getUserCount() != null ? req.getUserCount() : coupon.getUserCount();


            // فیلد های این بخش میتونن دقیق تر هم بررسی شن و توی یه if نباشن
            if (valueToCheck <= 0 || (typeToCheck == CouponType.PERCENT && valueToCheck > 100) || minPriceToCheck < 0 || userCountToCheck < 0) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid numeric values for coupon (value, minPrice, userCount) during update. Percent value should be between 0 and 100."));
                if(tx.isActive()) tx.rollback();
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


    private void handleDeleteCoupon(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        if (!Role.ADMIN.name().equals(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Admin access required."));
            return;
        }

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
                if(tx.isActive()) tx.rollback();
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
