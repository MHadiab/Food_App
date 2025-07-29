package dto;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantRequest {

    private String name;
    private String address;
    private String phone;
    @SerializedName("logoBase64")
    private String logoBase64;
    private Integer tax_fee;
    private Integer additional_fee;

    public RestaurantRequest() {
    }

}