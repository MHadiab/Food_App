package dto;

import entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeStatusRequest {
    private String order_id;
    private OrderStatus status;
}
