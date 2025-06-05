package response;

import entity.Rating;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
@Getter
@Setter
public class RateResponse {
    private long id;
    private long item_id;
    private Integer rating;
    private String comment;
    private ArrayList<String> imageBase64;
    private Long user_id;
    private LocalDateTime created_at;

    public RateResponse(Rating rating) {
        this.rating = rating.getRating();
        if (rating.getComment() != null) {
            this.comment = rating.getComment();
        }
        if (rating.getImageBase64() != null) {
            this.imageBase64 = new ArrayList<>(rating.getImageBase64());
        }
        this.created_at = rating.getCreated_at();
        this.user_id = rating.getUser_id();
    }
}
