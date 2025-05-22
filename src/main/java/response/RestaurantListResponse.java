package response;

import entity.Restaurant;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class RestaurantListResponse {
    private String message;
    private List<Restaurant> restaurants;

    public RestaurantListResponse(String message, List<Restaurant> restaurants) {
        this.message = message;
        this.restaurants = restaurants;
    }
}
