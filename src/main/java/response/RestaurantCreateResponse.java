package response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantCreateResponse {
    private String message;
    private Long restaurantId;

    public RestaurantCreateResponse(String message, Long restaurantId) {
        this.message = message;
        this.restaurantId = restaurantId;
    }
}
