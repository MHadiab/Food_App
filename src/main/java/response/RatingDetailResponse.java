package response;

import entity.Rating;
import lombok.Getter;
import lombok.Setter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RatingDetailResponse {

    private Long id;
    private Long orderId;
    private Long restaurantId;
    private Long userId;
    private Integer rating;
    private String comment;
    private List<Long> itemIds;
    private List<String> imageBase64;
    private String createdAt;

    public RatingDetailResponse(Rating rating) {
        this.id = rating.getId();
        if (rating.getOrder() != null) {
            this.orderId = (long) rating.getOrder().getId();
        }
        this.restaurantId = rating.getRestaurant_id();
        this.userId = rating.getUser_id();
        this.rating = rating.getRating();
        this.comment = rating.getComment();
        if (rating.getItemIds() != null) {
            this.itemIds = new ArrayList<>(rating.getItemIds());
        }
        if (rating.getImageBase64() != null) {
            this.imageBase64 = new ArrayList<>(rating.getImageBase64());
        }
        if (rating.getCreated_at() != null) {
            this.createdAt = rating.getCreated_at().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}