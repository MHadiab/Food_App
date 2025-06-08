package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(nullable = false)
    private String deliveryAddress;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Restaurant restaurant;

    @ElementCollection
    @CollectionTable(
            name="order_items",
            joinColumns=@JoinColumn(name="order_id")
    )
    private List<OrderItem> items = new ArrayList<>();

    private Long rawPrice;
    private int taxFee;
    private int additionalFee;
    private int courierFee;
    private Double payPrice;

    @Column(nullable = true)
    private Integer courierId=0;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}



