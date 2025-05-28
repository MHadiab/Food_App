package response;

import entity.Restaurant;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class RestaurantListResponse {
    private List<RestaurantResponse> restaurants;

    public RestaurantListResponse(List<RestaurantResponse> restaurants) {
        this.restaurants = restaurants;
    }
}