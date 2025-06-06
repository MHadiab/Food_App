package response;

import entity.FoodItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class FoodItemResponse {
    private Long id;
    private String name;
    private String imageBase64;
    private String description;
    private Integer price;
    private Integer supply;
    private List<String> keywords;
    private Long vendorId;

    public FoodItemResponse(FoodItem foodItem) {
        this.id = foodItem.getId();
        this.name = foodItem.getName();
        this.imageBase64 = foodItem.getImageBase64();
        this.description = foodItem.getDescription();
        this.price = foodItem.getPrice();
        this.supply = foodItem.getSupply();
        this.keywords = foodItem.getKeywords() != null ?
                new java.util.ArrayList<>(foodItem.getKeywords()) : null;
        if (foodItem.getRestaurant() != null) {
            this.vendorId = Long.valueOf(foodItem.getRestaurant().getId());
        }
    }
}