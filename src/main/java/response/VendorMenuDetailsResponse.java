package response;

import entity.Restaurant;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class VendorMenuDetailsResponse {
    private RestaurantResponse vendor; // Assuming RestaurantResponse exists as per HttpRestaurantHandler
    private List<String> menuTitles;
    private Map<String, List<FoodItemResponse>> menus; // Key is menu title

    public VendorMenuDetailsResponse(Restaurant restaurantEntity, List<String> menuTitles, Map<String, List<FoodItemResponse>> menus) {
        this.vendor = new RestaurantResponse(restaurantEntity); // Adapt if constructor differs
        this.menuTitles = menuTitles;
        this.menus = menus;
    }
}