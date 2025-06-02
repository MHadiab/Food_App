package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.FoodItemRequest;
import entity.FoodItem;
import entity.Menu;
import entity.Restaurant;
import entity.Role;
import org.hibernate.Session;
import org.hibernate.Transaction;
import response.FoodItemResponse;
import response.MessageResponse;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class HttpFoodItemHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            // POST /restaurants/{id}/item
            if (path.matches("/restaurants/\\d+/item") && "POST".equalsIgnoreCase(method)) {
                handleAddFoodItemToRestaurant(ex);
            }
            // PUT /restaurants/{id}/item/{item_id}
            else if (path.matches("/restaurants/\\d+/item/\\d+") && "PUT".equalsIgnoreCase(method)) {
                handleUpdateFoodItem(ex);
            }
            // DELETE /restaurants/{id}/item/{item_id}
            else if (path.matches("/restaurants/\\d+/item/\\d+") && "DELETE".equalsIgnoreCase(method)) {
                handleDeleteFoodItem(ex);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error")); // مشکل سرور
        }
    }

    private boolean isSellerAndOwner(HttpExchange ex, String token, Long restaurantId, Session session) throws IOException {
        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: Seller access required."));
            return false;
        }

        String sellerIdFromToken = JwtUtil.getUserIdFromToken(token);
        Restaurant restaurant = session.get(Restaurant.class, restaurantId);

        if (restaurant == null) {
            JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Restaurant not found."));
            return false;
        }

        if (restaurant.getSeller_id() != (Long.valueOf(sellerIdFromToken))) {
            JsonHelper.sendJson(ex, 403, new MessageResponse("Forbidden: You do not own this restaurant."));
            return false;
        }
        return true;
    }


    // با این متد از تکرار کد برای بررسی وجود ایتم در رستوران برای اپدیت و حذف استفاده میکنیم
    private FoodItem getAndValidateFoodItemForRestaurant(Session session, Long itemId, Long restaurantId, HttpExchange ex) throws IOException {
        FoodItem foodItem = session.get(FoodItem.class, itemId);
        if (foodItem == null || !foodItem.getRestaurant().getId().equals(restaurantId)) {
            JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Food item not found in this restaurant."));
            return null;
        }
        return foodItem;
    }  // اگر توی تست های مشکل داشتیم این بخش باید مجدد چک شه


    private void handleAddFoodItemToRestaurant(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]); // جداسازی ایدی رستوران
        if (ErrorHandler.FindError(ex, token)) return;


        FoodItemRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                FoodItemRequest.class
        );


        // این بخش بعدا باید بهتر مدیریت بشه
        if (req.getName() == null || req.getDescription() == null || req.getPrice() == null || req.getSupply() == null || req.getKeywords() == null || req.getKeywords().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: name, description, price, supply, and at least one keyword are required."));
            return;
        }
        if (req.getPrice() < 0 || req.getSupply() < 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: price and supply cannot be negative."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return; // بررسی وجود رستوان و بررسی مالکیت شخص

            Transaction tx = session.beginTransaction();
            Restaurant restaurant = session.get(Restaurant.class, restaurantId);

            FoodItem foodItem = new FoodItem();
            foodItem.setName(req.getName());
            foodItem.setImageBase64(req.getImageBase64());
            foodItem.setDescription(req.getDescription());
            foodItem.setPrice(req.getPrice());
            foodItem.setSupply(req.getSupply());
            foodItem.setKeywords(req.getKeywords());
            foodItem.setRestaurant(restaurant);
            foodItem.setActive(true);

            session.persist(foodItem);
            tx.commit();

            JsonHelper.sendJson(ex, 200, new FoodItemResponse(foodItem)); 
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while adding food item."));
        }
    }

    private void handleUpdateFoodItem(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        Long itemId = Long.parseLong(pathParts[4]); // جدا کردن ایدی غذا از دستور
        if (ErrorHandler.FindError(ex, token)) return;

        FoodItemRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                FoodItemRequest.class
        );

        if (req.getPrice() != null && req.getPrice() < 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: price cannot be negative."));
            return;
        }
        if (req.getSupply() != null && req.getSupply() < 0) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: supply cannot be negative."));
            return;
        }
        if (req.getKeywords() != null && req.getKeywords().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: keywords list cannot be empty if provided for update."));
            return;
        }


        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;

            //  متد کمکی برای دریافت و اعتبارسنجی آیتم غذایی
            FoodItem foodItem = getAndValidateFoodItemForRestaurant(session, itemId, restaurantId, ex);
            if (foodItem == null) { //   اگر null بود، یعنی خطای 404 توسط متد کمکی ارسال شده و ریترن میکنیم
                return;
            }


            Transaction tx = null;
            try {
                tx = session.beginTransaction();

                // اعمال تغییرات از req به foodItem
                if (req.getName() != null) foodItem.setName(req.getName());
                if (req.getImageBase64() != null) foodItem.setImageBase64(req.getImageBase64());
                if (req.getDescription() != null) foodItem.setDescription(req.getDescription());
                if (req.getPrice() != null) foodItem.setPrice(req.getPrice());
                if (req.getSupply() != null) foodItem.setSupply(req.getSupply());
                if (req.getKeywords() != null && !req.getKeywords().isEmpty()) foodItem.setKeywords(req.getKeywords());

                session.merge(foodItem);
                tx.commit();
                JsonHelper.sendJson(ex, 200, new FoodItemResponse(foodItem));
            } catch (Exception e) {
                if (tx != null && tx.isActive()) {
                    tx.rollback();  // اگر خطایی بود تغیری نده توی دیتابیس
                }
                e.printStackTrace();
                JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while updating food item."));
            }
        } catch (IOException e) { // برای GSON.fromJson
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid request body."));
        } catch (NumberFormatException e) { // برای parseLong
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid ID format in URL."));
        } catch (Exception e) { // خطاهای عمومی دیگر
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("An unexpected error occurred."));
        }
    }

    private void handleDeleteFoodItem(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        Long itemId = Long.parseLong(pathParts[4]);

        if (ErrorHandler.FindError(ex, token)) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;

            //  متد کمکی برای دریافت و اعتبارسنجی آیتم غذایی
            FoodItem foodItem = getAndValidateFoodItemForRestaurant(session, itemId, restaurantId, ex);
            if (foodItem == null) { //   اگر null بود، یعنی خطای 404 توسط متد کمکی ارسال شده و ریترن میکنیم
                return;
            }

            Transaction tx = null;
            try {
                tx = session.beginTransaction();

                // بررسی میکنیم اگر غذا در منویی وجود دارد اون هارو هم حذف کنیم
                if (foodItem.getMenus() != null) {
                    for (Menu menu : foodItem.getMenus()) {
                        menu.getItems().remove(foodItem); // حذف غذا از منویی که در آن وجود دارد
                        session.merge(menu); // Update the menu
                    }
                    foodItem.getMenus().clear();   // قطع ارتباط از سمت ایتم
                }
                session.merge(foodItem);

                session.remove(foodItem); // Delete the food item
                tx.commit();
                JsonHelper.sendJson(ex, 200, new MessageResponse("Food item removed successfully"));
            } catch (Exception e) {
                if (tx != null && tx.isActive()) {
                    tx.rollback();
                }
                e.printStackTrace();
                JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while deleting food item."));
            }
        } catch (IOException e) { // برای GSON.fromJson
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid request body."));
        } catch (NumberFormatException e) { // برای parseLong
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid ID format in URL."));
        } catch (Exception e) { // خطاهای عمومی دیگر
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("An unexpected error occurred."));
        }
    }
}