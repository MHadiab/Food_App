package entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class OrderItem {
    @Column(name="item_id", nullable=false)
    private Integer itemId;

    @Column(nullable=false)
    private Integer quantity;

    // ctor, getters & setters
    public OrderItem() {}
    public OrderItem(Integer itemId, Integer quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }
}

