package response;

import entity.Menu;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuResponse {
    private Long id; // need to check!!
    private String title;
    private Long restaurantId;

    public MenuResponse(Menu menu) {
        this.id = menu.getId();
        this.title = menu.getTitle();
        if (menu.getRestaurant() != null) {
            this.restaurantId = menu.getRestaurant().getId();
        }
    }
}
