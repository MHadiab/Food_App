package dto;

import entity.OrderStatus;

public class ChangeStatusRequest {
    private String order_id;
    private OrderStatus status;
}
