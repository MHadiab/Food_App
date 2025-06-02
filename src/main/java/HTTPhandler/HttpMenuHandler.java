package HTTPhandler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dto.AddItemToMenuRequest;
import dto.MenuRequest;
import entity.FoodItem;
import entity.Menu;
import entity.Restaurant;
import entity.Role;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import response.MenuResponse;
import response.MessageResponse;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class HttpMenuHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            // POST /restaurants/{id}/menu
            if (path.matches("/restaurants/\\d+/menu") && "POST".equalsIgnoreCase(method)) {
                handleAddMenuToRestaurant(ex);
            }
            // DELETE /restaurants/{id}/menu/{title}
            else if (path.matches("/restaurants/\\d+/menu/[^/]+") && "DELETE".equalsIgnoreCase(method) && path.split("/").length == 5) {
                handleDeleteMenuFromRestaurant(ex);
            }
            // PUT /restaurants/{id}/menu/{title} (Add item to menu)
            else if (path.matches("/restaurants/\\d+/menu/[^/]+") && "PUT".equalsIgnoreCase(method) && path.split("/").length == 5) {
                handleAddItemToMenu(ex);
            }
            // DELETE /restaurants/{id}/menu/{title}/{item_id}
            else if (path.matches("/restaurants/\\d+/menu/[^/]+/\\d+") && "DELETE".equalsIgnoreCase(method)) {
                handleDeleteItemFromMenu(ex);
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


    // متدی برای جلوگیری از تکرار کد در متد های حذف و اضافه کردن ایتم
    private Menu getAndValidateMenuByTitleForRestaurant(Session session, String menuTitle, Long restaurantId, HttpExchange ex) throws IOException {
        Menu menu = session.createQuery("FROM Menu WHERE title = :title AND restaurant.id = :restaurantId", Menu.class)
                .setParameter("title", menuTitle)
                .setParameter("restaurantId", restaurantId)
                .uniqueResult();

        if (menu == null) {
            JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Menu not found."));
            return null; // متد کمکی در صورت پیدا نشدن منو، null برمی‌گرداند و پاسخ خطا را ارسال می‌کند
        }
        return menu;
    }  // اگر توی تست مشکلی خاصی داشتیم این بخش باید مجدد تست شه

    private void handleAddMenuToRestaurant(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]); // دریافت ایدی رستوران

        if (ErrorHandler.FindError(ex, token)) return;

        MenuRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                MenuRequest.class
        );


        // بررسی نال نبودن تایتل
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: title is required."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;  // بررسی دارا بودن رستوران توسط شخص و همچنین وجود رستوران

            Transaction tx = session.beginTransaction();
            Restaurant restaurant = session.get(Restaurant.class, restaurantId);


            // منو اینجاد میشه با ارسال تایتلش و رستورانش
            Menu menu = new Menu();
            menu.setTitle(req.getTitle());
            menu.setRestaurant(restaurant);

            session.persist(menu);
            tx.commit();


            JsonHelper.sendJson(ex, 200, new MenuResponse(menu.getTitle()));  // موفقیت در ایجاد منو
        } catch (ConstraintViolationException e) {  //   تایتل داده شده برای منو در این رستوران وجود داشته
            JsonHelper.sendJson(ex, 409, new MessageResponse("Conflict: Menu title already exists for this restaurant."));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while adding menu."));
        }
    }

    private void handleDeleteMenuFromRestaurant(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);  // دریافت ایدی رستوران
        String menuTitle = pathParts[4];   // دریافت تایتل داده شده

        if (ErrorHandler.FindError(ex, token)) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);

            if (menu == null) {  // یعنی در متد بالا خطا پرتاب شده
                return;
            }

            //  حذف ایتم های مربوط به این منو
            menu.getItems().clear();
            session.merge(menu);

            session.remove(menu); // در اینجا منو رو پاک میکنیم
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Food menu removed from restaurant successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while deleting menu."));
        }
    }

    private void handleAddItemToMenu(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);  // ایدی رستوران
        String menuTitle = pathParts[4];  // تایتل

        if (ErrorHandler.FindError(ex, token)) return;

        AddItemToMenuRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                AddItemToMenuRequest.class
        );

        if (req.getItemId() == null) {  // درخواست نامعتبر
            JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: item_id is required."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);

            if (menu == null) {  // یعنی در متد بالا خطا پرتاب شده
                return;
            }

            FoodItem foodItem = session.get(FoodItem.class, req.getItemId());
            if (foodItem == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Food item not found."));
                return;
            }

            // بررسی وجود این ایتم در همین رستوران
            if (!foodItem.getRestaurant().getId().equals(restaurantId)) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: Food item does not belong to this restaurant."));
                return;
            }

            if (!foodItem.isActive()) {
                JsonHelper.sendJson(ex, 400, new MessageResponse("Invalid input: Cannot add inactive food item to menu."));
                return;
            }


            // بررسی وجود ایتم در این منو از قبل
            if (menu.getItems().contains(foodItem)) {
                JsonHelper.sendJson(ex, 409, new MessageResponse("Conflict: Food item already exists in this menu."));
                return;
            }

            menu.getItems().add(foodItem);
            session.merge(menu);
            tx.commit();

            JsonHelper.sendJson(ex, 200, new MessageResponse("Food item added to menu successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while adding item to menu."));
        }
    }

    private void handleDeleteItemFromMenu(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null ? auth.substring(7) : null;

        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);  // ایدی رستوران
        String menuTitle = pathParts[4];  // تایتل منو
        Long itemId = Long.parseLong(pathParts[5]); //  ایدی ایتم

        if (ErrorHandler.FindError(ex, token)) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (!isSellerAndOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);

            if (menu == null) {  // یعنی در متد بالا خطا پرتاب شده
                return;
            }

            FoodItem foodItem = session.get(FoodItem.class, itemId);
            if (foodItem == null) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Food item not found."));
                return;
            }


            // این ایتم اصلا در این منو نیست
            if (!menu.getItems().contains(foodItem)) {
                JsonHelper.sendJson(ex, 404, new MessageResponse("Resource not found: Food item not found in this menu."));
                return;
            }

            menu.getItems().remove(foodItem);
            session.merge(menu);
            tx.commit();

            JsonHelper.sendJson(ex, 200, new MessageResponse("Item removed from restaurant menu successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new MessageResponse("Internal server error while deleting item from menu."));
        }
    }
}