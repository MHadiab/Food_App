package HTTPhandler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class RestaurantDispatcher implements HttpHandler {
    private final RestaurantHandler restaurantHandler = new RestaurantHandler();
    private final FoodItemHandler itemHandler = new FoodItemHandler();
    private final MenuHandler menuHandler = new MenuHandler();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        System.out.println("RestaurantDispatcher received request");
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        if (path.matches("^/restaurants/\\d+/(item|items)(/.*)?$")) {
            System.out.println("Im here2");
            itemHandler.handle(ex);
            return;
        }
        if (path.matches("^/restaurants/\\d+/(menu|menus)(/.*)?$")) {
            System.out.println("Im here3");
            menuHandler.handle(ex);
            return;
        }
        else {
            System.out.println("Im here4-------------------------------------");
        }
        restaurantHandler.handle(ex);
    }
}





