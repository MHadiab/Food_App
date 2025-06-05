package dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class RatingUpdateRequest {
    private Integer rating;
    private String comment;
    private List<String> imageBase64;
}