package dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ItemFilterRequest {
    private String search;
    private Integer price;
    private List<String> keywords;
}