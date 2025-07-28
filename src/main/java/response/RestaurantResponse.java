package response;

import lombok.Getter;
import lombok.Setter;
import entity.Restaurant;

@Getter
@Setter
public class RestaurantResponse {
    private Integer id;
    private String name;
    private String address;
    private String phone;
    private String logoBase64;
    private Integer tax_fee;
    private Integer additional_fee;
    private Long sellerId;
    private Boolean active;

    public RestaurantResponse(Restaurant restaurant) {
        this.id = restaurant.getId();
        this.name = restaurant.getName();
        this.address = restaurant.getAddress();
        this.phone = restaurant.getPhone();
        this.logoBase64 = restaurant.getLogoBase64();
        this.tax_fee = restaurant.getTax_fee();
        this.additional_fee = restaurant.getAdditional_fee();
        this.sellerId = restaurant.getSeller_id();
        this.active = true;
    }
}