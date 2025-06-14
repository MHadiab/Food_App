package dto;

import entity.OrderItem;
import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {
    private String delivery_address;
    private Integer vendor_id;
    private String coupon_id;
    private ArrayList<OrderItemDTO> items;
}
