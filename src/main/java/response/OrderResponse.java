package response;

import entity.Order;
import entity.OrderItem;
import entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrderResponse {
    private int id;
    private String delivery_address;
    private int customer_id;
    private int vendor_id;
    private List<OrderItem> items;
    private int raw_price;
    private int tax_fee;
    private int additional_fee;
    private int courier_fee;
    private int pay_price;
    private int courier_id;
    private OrderStatus status;
    private String createdAt;
    private String updatedAt;


    public OrderResponse(Order order) {
        this.id = order.getId();
        this.delivery_address = order.getDeliveryAddress();
        this.customer_id= Math.toIntExact(order.getUser().getUser_id());
        this.vendor_id= Math.toIntExact(order.getRestaurant().getId());
        this.items=order.getItems();
        this.raw_price=order.getRawPrice();
        this.tax_fee=order.getTaxFee();
        this.additional_fee=order.getAdditionalFee();
        this.courier_fee=order.getCourierFee();
        this.pay_price=order.getPayPrice();
        this.courier_id=order.getCourierId();
        this.status=order.getStatus();
        this.createdAt=order.getCreatedAt().toString();
        if (order.getUpdatedAt() != null) {
            this.updatedAt=order.getUpdatedAt().toString();
        }else {
            this.updatedAt=null;
        }
    }

}
