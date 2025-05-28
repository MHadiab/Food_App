package response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantUpdateResponse {
    private String message;
    private Long restaurantId;

    public RestaurantUpdateResponse(String message, Long restaurantId) {
        this.message = message;
        this.restaurantId = restaurantId;
    }
}