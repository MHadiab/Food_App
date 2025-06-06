package dto;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
public class RateRequest {
    private Long order_id;
    private Integer rating;
    private String comment;

    @SerializedName("imageBase64")
    private ArrayList<String> imageBase64 = new ArrayList<>();
}
