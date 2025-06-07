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
import response.ErrorResponse;
import response.MenuResponse;
import response.MessageResponse;
import util.ErrorHandler;
import util.HibernateUtil;
import util.JsonHelper;
import util.JwtUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MenuHandler implements HttpHandler {

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
            if (path.matches("/restaurants/\\d+/menu") && "POST".equalsIgnoreCase(method)) {
                handleAddMenuToRestaurant(ex, token);
            } else if (path.matches("/restaurants/\\d+/menu/[^/]+") && "DELETE".equalsIgnoreCase(method) && path.split("/").length == 5) {
                handleDeleteMenuFromRestaurant(ex, token);
            } else if (path.matches("/restaurants/\\d+/menu/[^/]+") && "PUT".equalsIgnoreCase(method) && path.split("/").length == 5) {
                handleAddItemToMenu(ex, token);
            } else if (path.matches("/restaurants/\\d+/menu/[^/]+/\\d+") && "DELETE".equalsIgnoreCase(method)) {
                handleDeleteItemFromMenu(ex, token);
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
        if (userRole == null || !userRole.equalsIgnoreCase(Role.SELLER.name())) {
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

    private Menu getAndValidateMenuByTitleForRestaurant(Session session, String menuTitle, Long restaurantId, HttpExchange ex) throws IOException {
        Menu menu = session.createQuery("FROM Menu WHERE title = :title AND restaurant.id = :restaurantId", Menu.class)
                .setParameter("title", menuTitle)
                .setParameter("restaurantId", restaurantId)
                .uniqueResult();

        if (menu == null) {
            JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Menu not found."));
            return null;
        }
        return menu;
    }

    private void handleAddMenuToRestaurant(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        MenuRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                MenuRequest.class
        );

        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: title is required."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Restaurant restaurant = session.get(Restaurant.class, restaurantId);


            Menu menu = new Menu();
            menu.setTitle(req.getTitle());
            menu.setRestaurant(restaurant);

            session.persist(menu);
            tx.commit();


            JsonHelper.sendJson(ex, 200, new MenuResponse(menu.getTitle()));
        } catch (ConstraintViolationException e) {
            JsonHelper.sendJson(ex, 409, new ErrorResponse("Conflict: Menu title already exists for this restaurant."));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while adding menu."));
        }
    }

    private void handleDeleteMenuFromRestaurant(HttpExchange ex, String token) throws IOException {


        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        String menuTitle = pathParts[4];

        if (ErrorHandler.FindError(ex, token)) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);
            if (menu == null) {
                return;
            }

            menu.getItems().clear();
            session.merge(menu);
            session.remove(menu);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new ErrorResponse("Food menu removed from restaurant successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while deleting menu."));
        }
    }

    private void handleAddItemToMenu(HttpExchange ex, String token) throws IOException {


        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        String menuTitle = pathParts[4];

        if (ErrorHandler.FindError(ex, token)) return;

        AddItemToMenuRequest req = GSON.fromJson(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8),
                AddItemToMenuRequest.class
        );

        if (req.getItemId() == null) {
            JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: item_id is required."));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);

            if (menu == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found"));
                return;
            }

            FoodItem foodItem = session.get(FoodItem.class, req.getItemId());
            if (foodItem == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Food item not found."));
                return;
            }

            if (!foodItem.getRestaurant().getId().toString().equals(restaurantId.toString())) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: Food item does not belong to this restaurant."));
                return;
            }

            if (!foodItem.isActive()) {
                JsonHelper.sendJson(ex, 400, new ErrorResponse("Invalid input: Cannot add inactive food item to menu."));
                return;
            }

            if (menu.getItems().contains(foodItem)) {
                JsonHelper.sendJson(ex, 409, new ErrorResponse("Conflict: Food item already exists in this menu."));
                return;
            }
            menu.getItems().add(foodItem);
            session.merge(menu);
            tx.commit();
            JsonHelper.sendJson(ex, 200, new MessageResponse("Food item added to menu successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while adding item to menu."));
        }
    }

    private void handleDeleteItemFromMenu(HttpExchange ex, String token) throws IOException {
        if (ErrorHandler.FindError(ex, token)) return;
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Long restaurantId = Long.parseLong(pathParts[2]);
        String menuTitle = pathParts[4];
        Long itemId = Long.parseLong(pathParts[5]);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (wrongSellerOrOwner(ex, token, restaurantId, session)) return;

            Transaction tx = session.beginTransaction();
            Menu menu = getAndValidateMenuByTitleForRestaurant(session, menuTitle, restaurantId, ex);
            if (menu == null) {
                return;
            }
            FoodItem foodItem = session.get(FoodItem.class, itemId);
            if (foodItem == null) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Food item not found."));
                return;
            }


            if (!menu.getItems().contains(foodItem)) {
                JsonHelper.sendJson(ex, 404, new ErrorResponse("Resource not found: Food item not found in this menu."));
                return;
            }

            menu.getItems().remove(foodItem);
            session.merge(menu);
            tx.commit();

            JsonHelper.sendJson(ex, 200, new MessageResponse("Item removed from restaurant menu successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendJson(ex, 500, new ErrorResponse("Internal server error while deleting item from menu."));
        }
    }
}