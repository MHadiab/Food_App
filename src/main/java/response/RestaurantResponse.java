package response;

import lombok.Getter;
import lombok.Setter;
import entity.Restaurant;

@Getter
@Setter
public class RestaurantResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private String logoBase64;
    private Integer taxFee;
    private Integer additionalFee;

    public RestaurantResponse(Restaurant restaurant) {
        this.id = restaurant.getId();
        this.name = restaurant.getName();
        this.address = restaurant.getAddress();
        this.phone = restaurant.getPhone();
        this.logoBase64 = restaurant.getLogoBase64();
        this.taxFee = restaurant.getTax_fee();
        this.additionalFee = restaurant.getAdditional_fee();
    }
}