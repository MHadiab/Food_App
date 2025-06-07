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
import response.ErrorResponse;
import response.FoodItemResponse;
import response.ErrorResponse;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FoodItemHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            String auth;
            try {
                auth = ex.getRequestHeaders().getFirst("Authorization");
            } catch (Exception e) {
                JsonHelper.sendJson(ex, 401, new ErrorResponse("Unauthorized request"));
                return;
            }
            String token = auth.substring(7);

            if (path.matches("/restaurants/\\d+/item") && "POST".equalsIgnoreCase(method)) {
                handleAddFoodItemToRestaurant(ex, token);
            } else if (path.matches("/restaurants/\\d+/item/\\d+") && "PUT".equalsIgnoreCase(method)) {
                handleUpdateFoodItem(ex, token);
            } else if (path.matches("/restaurants/\\d+/item/\\d+") && "DELETE".equalsIgnoreCase(method)) {
                handleDeleteFoodItem(ex, token);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error"));
        }
    }

    private boolean wrongSellerOrOwner(HttpExchange ex, String token, Long restaurantId, Session session) throws IOException {
        String userRole = JwtUtil.getRoleFromToken(token);
        if (userRole == null || !userRole.equals(Role.SELLER.name())) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden: Seller access required."));
            return true;
        }

        String sellerIdFromToken = JwtUtil.getUserIdFromToken(token);
        Restaurant restaurant = session.get(Restaurant.class, restaurantId);

        if (restaurant == null) {
            JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Restaurant not found."));
            return true;
        }

        assert sellerIdFromToken != null;
        if (restaurant.getSeller_id() != (Long.parseLong(sellerIdFromToken))) {
            JsonHelper.sendJson(ex, 403, new ErrorResponse("Forbidden: You do not own this restaurant."));
            return true;
        }
        return false;
    }


    private FoodItem getAndValidateFoodItemForRestaurant(Session session, Long itemId, Long restaurantId, HttpExchange ex) throws IOException {
        FoodItem foodItem = session.get(FoodItem.class, itemId);
        if (foodItem == null || !foodItem.getRestaurant().getId().equals(restaurantId)) {
            JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Food item not found in this restaurant."));
            return null;
        }
        return foodItem;
    }  // اگر توی تست های مشکل داشتیم این بخش باید مجدد چک شه


    private void handleAddFoodItemToRestaurant(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);


        FoodItemRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                FoodItemRequest.class
        );


        if (req.getName() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid name"));
            return;
        }
        if (req.getDescription() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid description"));
            return;
        }
        if (req.getPrice() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid price"));
            return;
        }
        if (req.getSupply() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid supply"));
            return;

        }
        if (req.getKeywords() == null || req.getKeywords().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid keywords"));
            return;
        }
        if (req.getPrice() < 0 || req.getSupply() < 0) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: price and supply cannot be negative."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

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
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while adding food item."));
        }
    }

    private void handleUpdateFoodItem(HttpExchange ex ,String token) throws IOException {
        if (ErrorHandler.FindError(ex, token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        Long itemId = Long.parseLong(pathParts[4]); 

        FoodItemRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                FoodItemRequest.class
        );

        if (req.getPrice() != null && req.getPrice() < 0) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: price cannot be negative."));
            return;
        }
        if (req.getSupply() != null && req.getSupply() < 0) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: supply cannot be negative."));
            return;
        }
        if (req.getKeywords() != null && req.getKeywords().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: keywords list cannot be empty if provided for update."));
            return;
        }


        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            FoodItem foodItem = getAndValidateFoodItemForRestaurant(session, itemId, restaurantId, ex);
            if (foodItem == null) { 
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }


            Transaction tx = null;
            try {
                tx = session.beginTransaction();

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
                JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while updating food item."));
            }
        } catch (IOException e) { // برای GSON.fromJson
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid request body."));
        } catch (NumberFormatException e) { // برای parseLong
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid ID format in URL."));
        } catch (Exception e) { // خطاهای عمومی دیگر
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("An unexpected error occurred."));
        }
    }

    private void handleDeleteFoodItem(HttpExchange ex , String token) throws IOException {

        if (ErrorHandler.FindError(ex, token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        Long itemId = Long.parseLong(pathParts[4]);


        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            FoodItem foodItem = getAndValidateFoodItemForRestaurant(session, itemId, restaurantId, ex);
            if (foodItem == null) { 
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }

            Transaction tx = null;
            try {
                tx = session.beginTransaction();

                if (foodItem.getMenus() != null) {
                    for (Menu menu : foodItem.getMenus()) {
                        menu.getItems().remove(foodItem); 
                        session.merge(menu); 
                    }
                    foodItem.getMenus().clear();  
                }
                session.merge(foodItem);

                session.remove(foodItem);
                tx.commit();
                JsonHelper.sendJson(ex, 200, new ErrorResponse("Food item removed successfully"));
            } catch (Exception e) {
                if (tx != null && tx.isActive()) {
                    tx.rollback();
                }
                e.printStackTrace();
                JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while deleting food item."));
            }
        } catch (IOException e) { 
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid request body."));
        } catch (NumberFormatException e) { // برای parseLong
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid ID format in URL."));
        } catch (Exception e) { // خطاهای عمومی دیگر
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("An unexpected error occurred."));
        }
    }
}