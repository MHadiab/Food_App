package HTTPhandler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.swing.plaf.basic.BasicComboBoxUI;
import java.io.IOException;

public class RestaurantDispatcher implements HttpHandler {
    private final HttpRestaurantHandler restaurantHandler = new HttpRestaurantHandler();
    private final HttpFoodItemHandler itemHandler = new HttpFoodItemHandler();
    private final HttpMenuHandler menuHandler = new HttpMenuHandler();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        if (path.matches("^/restaurants/\\d+/item(/.*)?$")) {
            itemHandler.handle(ex);
            return;
        }
        if (path.matches("^/restaurants/\\d+/menu(/.*)?$")) {
            menuHandler.handle(ex);
            return;
        }
        restaurantHandler.handle(ex);
    }
}





