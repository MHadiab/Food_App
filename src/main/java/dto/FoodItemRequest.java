package dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class FoodItemRequest {
    private String name;
    private String imageBase64;
    private String description;
    private Integer price;
    private Integer supply;
    private List<String> keywords;
}