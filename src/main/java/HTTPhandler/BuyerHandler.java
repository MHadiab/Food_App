package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.*;
import entity.*;

import java.io.IOException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import response.*;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import javax.sound.midi.MidiFileFormat;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static util.splitQuery.splitQuery;

public class BuyerHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if ("POST".equalsIgnoreCase(method) && path.equals("/orders")) {
            handleCreateOrder(ex);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("^/orders/\\d+$")) {
            handleGetOrderDetail(ex);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/orders/history")) {
            handleOrderHistory(ex);
            return;
        }
        if ("PUT".equalsIgnoreCase(method) && path.matches("^/favorites/\\d+$")) {
            String[] parts = path.split("/");
            if (parts.length != 3) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                return;
            }
            String idStr = parts[2];
            long vendor_id = Long.parseLong(idStr);
            handleCreateFavorite(ex, vendor_id);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/favorites")) {
            handleGetFavorites(ex);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method) && path.matches("^/favorites/\\d+$")) {
            String[] parts = path.split("/");
            if (parts.length != 3) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                return;
            }
            String idStr = parts[2];
            long vendor_id = Long.parseLong(idStr);
            handleDeleteFavorite(ex, vendor_id);
            return;
        }
        if ("POST".equalsIgnoreCase(method) && path.equals("/ratings")) {
            handleCreateRate(ex);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("^/ratings/items/\\d+$")) {
            String[] parts = path.split("/");
            if (parts.length != 4) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input"));
                return;
            }
            String itemId = parts[3];
            handleGetRateOfItem(ex, itemId);
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/vendors".equals(path)) {
            handleListVendors(ex);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/vendors/\\d+")) {
            handleGetVendorDetails(ex);
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/items".equals(path)) {
            handleListItems(ex);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/items/\\d+")) {
            handleGetItemDetails(ex);
            return;
        }
        // دستور بررسی اعتبار کوپن
        if ("GET".equalsIgnoreCase(method) && "/coupons".equals(path)) {
            handleCheckCouponValidity(ex);
        }

        // سه دستور اخر بایر برای من
        if ("GET".equalsIgnoreCase(method) && path.matches("/ratings/\\d+")) {
            handleGetRatingDetails(ex);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method) && path.matches("/ratings/\\d+")) {
            handleDeleteRating(ex);
            return;
        }
        if ("PUT".equalsIgnoreCase(method) && path.matches("/ratings/\\d+")) {
            handleUpdateRating(ex);
            return;
        }
        ex.sendResponseHeaders(404, -1);
    }

    private void handleGetRateOfItem(HttpExchange ex, String itemId) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        // اگر لازم است فقط خریداران (BUYER) بتوانند این متد را فراخوانی کنند:
        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // ابتدا مطمئن می‌شویم itemId یک عدد صحیح است و آن آیتم واقعا وجود دارد
            int intItemId;
            try {
                intItemId = Integer.parseInt(itemId);
            } catch (NumberFormatException nfe) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid item ID format"));
                return;
            }

            FoodItem foodItem = session.get(FoodItem.class, (long) intItemId);
            if (foodItem == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: item not found"));
                return;
            }

            // HQL: دقت کنید i از نوع Integer است
            String hql = "SELECT r FROM Rating r JOIN r.itemIds i WHERE i = :id";
            Query<Rating> reviewsQuery = session.createQuery(hql, Rating.class);
            reviewsQuery.setParameter("id", intItemId); // ← همین‌جا Integer بدهید، نه Long

            // بگیریم لیست ریویوها را
            List<Rating> ratingList = reviewsQuery.list();

            // اگر هیچ ریویویی نبود، پاسخ بدهیم
            if (ratingList.isEmpty()) {
                Map<String, Object> emptyResponse = new LinkedHashMap<>();
                emptyResponse.put("avg_rating", 0.0);
                emptyResponse.put("comments", Collections.emptyList());
                JsonHelper.sendJson(ex, 200, emptyResponse);
                return;
            }

            // تبدیل به RateResponse و محاسبه total
            List<RateResponse> comments = ratingList.stream()
                    .map(RateResponse::new)
                    .toList();

            long total = 0;
            for (RateResponse r : comments) {
                r.setItem_id(Long.parseLong(itemId));
                total += r.getRating();
            }

            double average = (double) total / comments.size();

            // ساخت Map<String,Object> با کلیدهای مناسب
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("avg_rating", average);
            response.put("comments", comments);

            JsonHelper.sendJson(ex, 200, response);

        } catch (Exception e) {
            // این‌جا هر استثنای دیگری (از جمله IllegalArgumentException‌ که ممکن بود پیش بیاید) را بگیریم
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }


    private void handleCreateRate(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        // فقط خریداران می‌توانند امتیاز دهند
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
            return;
        }
        String userId = JwtUtil.getUserIdFromToken(token);

        RateRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), UTF_8),
                RateRequest.class
        );
        System.out.println("DEBUG: req.imageBase64 = " + req.getImageBase64());
        if (req == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid request"));
            return;
        }
        if (req.getRating() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid rating"));
            return;
        }
        if (req.getOrder_id() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid order_id"));
            return;
        }
        if (req.getImageBase64() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid imageBase64"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            User user = session.get(User.class, userId);
            Order order = session.get(Order.class, req.getOrder_id());
            if (order == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid order_id"));
                return;
            }
            if (!order.getUser().getUser_id().equals(user.getUser_id())) {
                JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
                return;
            }
            if (req.getImageBase64() == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid image"));
                return;
            }
            Query<Rating> existing = session.createQuery(
                    "FROM Rating r WHERE r.order.id = :orderId AND r.user_id = :userId",
                    Rating.class
            );
            existing.setParameter("orderId", req.getOrder_id());
            existing.setParameter("userId", user.getUser_id());
            if (!existing.list().isEmpty()) {
                // یعنی رکوردی هست؛ پس به کاربر بگویید قبلاً ثبت کرده
                JsonHelper.sendJson(ex, 400, new ErrorResponse("You have already rated this order."));
                return;
            }
            Rating rating = new Rating();
            rating.setOrder(order);
            rating.setRestaurant_id(Long.valueOf(order.getRestaurant().getId()));
            rating.setUser_id(user.getUser_id());
            rating.setRating(req.getRating());
            if (req.getComment() != null) {
                rating.setComment(req.getComment());
            }
            rating.setCreated_at(LocalDateTime.now());

            // اگر req.getImageBase64() نال بود، از لیست خالی استفاده کن:
            if (req.getImageBase64() != null) {
                rating.setImageBase64(new ArrayList<>(req.getImageBase64()));
            } else {
                rating.setImageBase64(new ArrayList<>());
            }

            // پرکردن itemIds از روی آیتم‌های خود سفارش
            for (OrderItem orderItem : order.getItems()) {
                Integer itemId = orderItem.getItemId();
                rating.getItemIds().add(Long.valueOf(itemId));
            }

            // حالا حتماً Rating را ذخیره کن و تراکنش را commit کن
            session.persist(rating);
            tx.commit();

            JsonHelper.sendJson(ex, 200, new MessageResponse("Rating submitted"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }


    private void handleDeleteFavorite(HttpExchange ex, long vendorId) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        long userId = Long.parseLong(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            User user = session.get(User.class, userId);
            Set<Restaurant> favorites = user.getFavorites();
            Restaurant restaurant = session.get(Restaurant.class, vendorId);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid id"));
                return;
            }
            if (!favorites.contains(restaurant)) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }
            user.getFavorites().remove(restaurant);
            session.merge(user);
            transaction.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Removed from favorites"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleGetFavorites(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        long userId = Long.parseLong(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            User user = session.get(User.class, userId);
            Set<Restaurant> favorites = user.getFavorites();
            Set<RestaurantResponse> resp = favorites.stream()
                    .map(RestaurantResponse::new)
                    .collect(Collectors.toSet());
            JsonHelper.sendJson(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleCreateFavorite(HttpExchange ex, long vendor_id) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        if (!Objects.requireNonNull(JwtUtil.getRoleFromToken(token)).equalsIgnoreCase("buyer")) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden request"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            Restaurant restaurant = (Restaurant) session.get(Restaurant.class, vendor_id);
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid id"));
                return;
            }
            Long userId = Long.valueOf(Objects.requireNonNull(JwtUtil.getUserIdFromToken(token)));
            User user = (User) session.get(User.class, userId);
            user.getFavorites().add(restaurant);
            session.merge(user);
            transaction.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Added to favorites"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleOrderHistory(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden: Only buyers can view order history"));
            return;
        }
        String userIdStr = JwtUtil.getUserIdFromToken(token);
        if (userIdStr == null) {
            JsonHelper.sendJson(ex, 401, new ErrorResponse("Invalid or expired token"));
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
                    JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid `vendor` parameter"));
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

    private void handleGetOrderDetail(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid id"));
            return;
        }
        int orderId;
        try {
            orderId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid id"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Order order = session.get(Order.class, orderId);
            OrderResponse resp = new OrderResponse(order);
            JsonHelper.sendJson(ex, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleCreateOrder(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);

        if (ErrorHandler.FindError(ex, token)) return;
        if (!"BUYER".equalsIgnoreCase(JwtUtil.getRoleFromToken(token))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden"));
            return;
        }
        CreateOrderRequest req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), UTF_8), CreateOrderRequest.class);
        if (req.getVendor_id() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid vendor_id"));
            return;
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid items"));
            return;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();

            Restaurant restaurant = session.get(Restaurant.class, req.getVendor_id());
            if (restaurant == null) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid vendor_id"));
                return;
            }

            User user = session.get(User.class, JwtUtil.getUserIdFromToken(token));

            List<OrderItem> orderItems = new ArrayList<>();
            double totalRawPrice = 0.0;

            for (OrderItemDTO dto : req.getItems()) {
                if (dto.getItem_id() == null) {
                    JsonHelper.sendJson(ex, 400, new ErrorResponse("Each item must have a non-null item_id"));
                    return;
                }
                if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                    JsonHelper.sendJson(ex, 400, new ErrorResponse(
                            "For item_id=" + dto.getItem_id() + ", quantity must be a positive number"
                    ));
                    return;
                }
                Integer itemId = dto.getItem_id();
                FoodItem foodItem = session.get(FoodItem.class, itemId);
                if (foodItem == null) {
                    JsonHelper.sendJson(ex, 400, new ErrorResponse(
                            "Invalid item_id: " + itemId + " does not exist in FoodItem"
                    ));
                    return;
                }
                double unitPrice = foodItem.getPrice(); // در صورت BigDecimal: foodItem.getPrice().doubleValue()
                int qty = dto.getQuantity();
                totalRawPrice += unitPrice * qty;
                orderItems.add(new OrderItem(itemId, qty));
            }
            Order order = new Order();
            order.setDeliveryAddress(user.getAddress());
            order.setUser(user);
            order.setRestaurant(restaurant);

            order.setItems(orderItems);
            order.setRawPrice((int) totalRawPrice);

            order.setTaxFee(restaurant.getTax_fee());
            order.setAdditionalFee(restaurant.getAdditional_fee());
            order.setCourierFee(0);

            order.setPayPrice((int) totalRawPrice + restaurant.getTax_fee() + restaurant.getAdditional_fee());

            order.setStatus(OrderStatus.SUBMITTED);
            order.setCreatedAt(LocalDateTime.now());
            session.persist(order);
            tx.commit();
            OrderResponse resp = new OrderResponse(order);
            JsonHelper.sendJson(ex, 200, resp);

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal Server Error"));
        }
    }

    private void handleListVendors(HttpExchange ex) throws IOException, IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can list vendors."));
            return;
        }

        VendorFilterRequest filterRequest = null;
        String contentLengthHeader = ex.getRequestHeaders().getFirst("Content-Length");
        try {
            if (contentLengthHeader != null && Integer.parseInt(contentLengthHeader) > 0) {
                InputStreamReader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
                filterRequest = GSON.fromJson(reader, VendorFilterRequest.class);
                if (filterRequest == null && Integer.parseInt(contentLengthHeader) > 0) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body for vendor filter (empty or malformed)."));
                    return;
                }
            }
        } catch (IOException e) { // خطای خواندن از بدنه درخواست
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error reading request body for vendor filter."));
            return;
        } catch (NumberFormatException e) { // خطای تبدیل Content-Length به عدد
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid Content-Length header for vendor filter."));
            return;
        } catch (JsonSyntaxException e) { // خطای پارس کردن JSON
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON syntax in request body for vendor filter."));
            return;
        } catch (Exception e) { // سایر خطا
            e.printStackTrace();
            JsonHelper.sendJson(ex, 400, new MessageResponse("Error processing request body for vendor filter."));
            return;
        }


        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder("SELECT r FROM Restaurant r WHERE r.active = true");
            Map<String, Object> parameters = new HashMap<>();

            if (filterRequest != null) {
                if (filterRequest.getSearch() != null && !filterRequest.getSearch().trim().isEmpty()) {
                    hql.append(" AND (LOWER(r.name) LIKE LOWER(:search) OR LOWER(r.address) LIKE LOWER(:search))");
                    parameters.put("search", "%" + filterRequest.getSearch().trim() + "%");
                }
                if (filterRequest.getKeywords() != null && !filterRequest.getKeywords().isEmpty()) {
                    List<String> keywordConditions = new ArrayList<>();
                    for (int i = 0; i < filterRequest.getKeywords().size(); i++) {
                        String keyword = filterRequest.getKeywords().get(i);
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            String paramName = "keyword" + i;
                            keywordConditions.add("(LOWER(r.name) LIKE LOWER(:" + paramName + ") OR LOWER(r.address) LIKE LOWER(:" + paramName + "))");
                            parameters.put(paramName, "%" + keyword.trim() + "%");
                        }
                    }
                    if (!keywordConditions.isEmpty()) {
                        hql.append(" AND (").append(String.join(" OR ", keywordConditions)).append(")");
                    }
                }
            }
            hql.append(" ORDER BY r.name ASC");

            Query<Restaurant> query = session.createQuery(hql.toString(), Restaurant.class);
            parameters.forEach(query::setParameter);

            List<Restaurant> vendors = query.list();
            List<RestaurantResponse> vendorResponses = vendors.stream()
                    .map(RestaurantResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, vendorResponses);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error fetching vendors."));
        }
    }

    private void handleGetVendorDetails(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can view vendor details."));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Integer vendorId;
        try {
            if (parts.length > 2) {
                vendorId = Integer.parseInt(parts[2]);
            } else {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Vendor ID missing in path. Expected /vendors/{id}"));
                return;
            }
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid vendor ID format in path. Must be a number."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Restaurant restaurant = session.get(Restaurant.class, vendorId);
            if (restaurant == null || !restaurant.isActive()) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Vendor not found or not active."));
                return;
            }

            // ۲.۱. ابتدا یک Map مرتب بساز (LinkedHashMap برای حفظ ترتیب insertion)
            Map<String, Object> responseMap = new LinkedHashMap<>();

            // ۲.۲. "vendor" را با RestaurantResponse پر می‌کنیم
            responseMap.put("vendor", new RestaurantResponse(restaurant));

            // ۲.۳. کوئری گرفتن منوها به ترتیبی که می‌خواهیم
            Query<Menu> menuQuery = session.createQuery(
                    "SELECT DISTINCT m FROM Menu m LEFT JOIN FETCH m.items item WHERE m.restaurant.id = :restaurantId ORDER BY m.title",
                    Menu.class);
            menuQuery.setParameter("restaurantId", vendorId);
            List<Menu> menusFromDb = menuQuery.list();

            // ۲.۴. لیست عناوین منو
            List<String> menuTitles = menusFromDb.stream()
                    .map(Menu::getTitle)
                    .collect(Collectors.toList());
            responseMap.put("menu_titles", menuTitles);

            // ۲.۵. حالا هر منو را به شکل کلیدی به همان نامِ title در Map می‌ریزیم
            for (Menu menu : menusFromDb) {
                // لیست آیتم‌های فعال را با responder می‌سازیم
                List<FoodItemResponse> activeItemResponses = menu.getItems().stream()
                        .filter(FoodItem::isActive)
                        .map(FoodItemResponse::new)
                        .collect(Collectors.toList());

                // کلید در JSON دقیقاً برابر عنوانِ منو (مثلا "reza_menu") باشد
                responseMap.put(menu.getTitle(), activeItemResponses);
            }

            // ۲.۶. در نهایت JSON را برگردانیم
            JsonHelper.sendJson(ex, 200, responseMap);

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error fetching vendor details: " + e.getMessage()));
        }
    }

    private void handleListItems(HttpExchange ex) throws IOException, IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can list items."));
            return;
        }

        ItemFilterRequest filterRequest = null;
        String contentLengthHeader = ex.getRequestHeaders().getFirst("Content-Length");
        try {
            if (contentLengthHeader != null && Integer.parseInt(contentLengthHeader) > 0) {
                InputStreamReader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
                filterRequest = GSON.fromJson(reader, ItemFilterRequest.class);
                if (filterRequest == null && Integer.parseInt(contentLengthHeader) > 0) {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON body for item filter (empty or malformed)."));
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error reading request body for item filter."));
            return;
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid Content-Length header for item filter."));
            return;
        } catch (JsonSyntaxException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid JSON syntax in request body for item filter."));
            return;
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 400, new MessageResponse("Error processing request body for item filter."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder("SELECT fi FROM FoodItem fi JOIN fi.restaurant r WHERE fi.active = true AND r.active = true");
            Map<String, Object> parameters = new HashMap<>();

            if (filterRequest != null) {
                if (filterRequest.getSearch() != null && !filterRequest.getSearch().trim().isEmpty()) {
                    hql.append(" AND (LOWER(fi.name) LIKE LOWER(:search) OR LOWER(fi.description) LIKE LOWER(:search))");
                    parameters.put("search", "%" + filterRequest.getSearch().trim() + "%");
                }
                if (filterRequest.getPrice() != null && filterRequest.getPrice() >= 0) {
                    hql.append(" AND fi.price <= :maxPrice");
                    parameters.put("maxPrice", filterRequest.getPrice());
                }
                if (filterRequest.getKeywords() != null && !filterRequest.getKeywords().isEmpty()) {
                    List<String> validKeywords = filterRequest.getKeywords().stream()
                            .filter(kw -> kw != null && !kw.trim().isEmpty())
                            .map(String::toLowerCase)
                            .collect(Collectors.toList());
                    if (!validKeywords.isEmpty()) {
                        hql.append(" AND EXISTS (SELECT kw FROM fi.keywords kw WHERE LOWER(kw) IN (:itemKeywords))");
                        parameters.put("itemKeywords", validKeywords);
                    }
                }
            }
            hql.append(" ORDER BY fi.name ASC");

            Query<FoodItem> query = session.createQuery(hql.toString(), FoodItem.class);
            parameters.forEach(query::setParameter);

            List<FoodItem> items = query.list();
            List<FoodItemResponse> itemResponses = items.stream()
                    .map(FoodItemResponse::new)
                    .collect(Collectors.toList());
            JsonHelper.sendJson(ex, 200, itemResponses);
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error fetching items."));
        }
    }

    private void handleGetItemDetails(HttpExchange ex) throws IOException, IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can view item details."));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        Long itemId;
        try {
            if (parts.length > 2) {
                itemId = Long.parseLong(parts[2]);
            } else {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Item ID missing in path. Expected /items/{id}"));
                return;
            }
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid item ID format in path. Must be a number."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            FoodItem item = session.get(FoodItem.class, itemId);
            if (item == null || !item.isActive() || item.getRestaurant() == null || !item.getRestaurant().isActive()) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Item not found, not active, or belongs to an inactive vendor."));
                return;
            }
            JsonHelper.sendJson(ex, 200, new FoodItemResponse(item));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Error fetching item details: " + e.getMessage()));
        }
    }

    private void handleCheckCouponValidity(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can check coupon validity."));
            return;
        }

        Map<String, String> queryParams = splitQuery(ex.getRequestURI().getQuery());
        String couponCode = queryParams != null ? queryParams.get("coupon_code") : null;

        if (couponCode == null || couponCode.trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Missing required query parameter: coupon_code."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            Query<Coupon> query = session.createQuery("FROM Coupon c WHERE c.couponCode = :code", Coupon.class);
            query.setParameter("code", couponCode.trim());
            Coupon coupon = query.uniqueResult();

            //   بررسی پیدا شدن کوپن
            if (coupon == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon not found."));
                return;
            }

            //  بررسی شرایط اعتبار کوپن
            LocalDate today = LocalDate.now();
            if (!coupon.isActive()) {  // کوپن هنوز موجوده یا نه
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon is not active."));
                return;
            }
            if (today.isBefore(coupon.getStartDate())) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon is not yet valid. It starts on " + coupon.getStartDate() + "."));
                return;
            }
            if (today.isAfter(coupon.getEndDate())) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon has expired."));
                return;
            }
            if (coupon.getTimesUsed() != null && coupon.getUserCount() != null && coupon.getTimesUsed() >= coupon.getUserCount()) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Coupon has reached its usage limit."));
                return;
            }

            // اگر تمام بررسی‌ها موفقیت‌آمیز بود، کوپن معتبر است
            JsonHelper.sendJson(ex, 200, new CouponResponse(coupon));

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while checking coupon validity."));
        }
    }

    private void handleGetRatingDetails(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can access ratings."));
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        Long ratingId;
        try {
            ratingId = Long.parseLong(parts[2]);  // استخراج ایدی نمره دهی
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid rating ID format."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Rating rating = session.get(Rating.class, ratingId);

            if (rating == null) {  //  نمره دهی با این ایدی پیدا نشد
                JsonHelper.sendJson(ex, 404, new MessageResponse("Rating not found."));
                return;
            }

            Long userIdFromToken = Long.valueOf(JwtUtil.getUserIdFromToken(token));  // ایدی کسی که این نظر رو ثبت کرده
            if (!rating.getUser_id().equals(userIdFromToken)) {  // این ایدی با ایدی کسی که نظر رو ثبت کرده یکی نیست
                JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: You are not the owner of this rating."));
                return;
            }

            JsonHelper.sendJson(ex, 200, new RatingDetailResponse(rating));

        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while fetching rating."));
        }
    }


    private void handleDeleteRating(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can delete ratings."));
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        Long ratingId;
        try {
            ratingId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid rating ID format."));
            return;
        }

        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Rating rating = session.get(Rating.class, ratingId);

            if (rating == null) {  //  نمره دهی با این ایدی پیدا نشد
                JsonHelper.sendJson(ex, 404, new MessageResponse("Rating not found."));
                if (tx.isActive()) tx.rollback();
                return;
            }

            Long userIdFromToken = Long.valueOf(JwtUtil.getUserIdFromToken(token));
            if (!rating.getUser_id().equals(userIdFromToken)) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: You are not the owner of this rating."));
                if (tx.isActive()) tx.rollback();
                return;
            }

            session.remove(rating);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Rating deleted successfully."));

        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while deleting rating."));
        }
    }

    private void handleUpdateRating(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth.substring(7);
        if (ErrorHandler.FindError(ex, token)) return;

        String role = JwtUtil.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("BUYER")) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Only buyers can update ratings."));
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        Long ratingId;
        try {
            ratingId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid rating ID format."));
            return;
        }

        RatingUpdateRequest req;
        try {
            req = GSON.fromJson(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8), RatingUpdateRequest.class);
            if (req == null) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Request body is empty or malformed."));
                return;
            }
        } catch (Exception e) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid request body format."));
            return;
        }

        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Rating rating = session.get(Rating.class, ratingId);

            if (rating == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Rating not found."));
                if (tx.isActive()) tx.rollback();
                return;
            }

            Long userIdFromToken = Long.valueOf(JwtUtil.getUserIdFromToken(token));
            if (!rating.getUser_id().equals(userIdFromToken)) {
                JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: You are not the owner of this rating."));
                if (tx.isActive()) tx.rollback();
                return;
            }

            // این بخش بعدا میتنه بهتر بشه فعلا همین طوری یه بررسی ساده انجام دادم
            if (req.getRating() != null) {
                if (req.getRating() >= 1 && req.getRating() <= 5) {
                    rating.setRating(req.getRating());
                } else {
                    JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid rating value. Must be between 1 and 5."));
                    if (tx.isActive()) tx.rollback();
                    return;
                }
            }
            if (req.getComment() != null) {
                rating.setComment(req.getComment());
            }
            if (req.getImageBase64() != null) {
                rating.setImageBase64(req.getImageBase64());
            }

            session.merge(rating);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new RatingDetailResponse(rating));

        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while updating rating."));
        }
    }
}




