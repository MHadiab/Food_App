package entity;

import entity.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long restaurant_id;

    @Column(nullable = false)
    private Long user_id;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = true)
    private String comment;

    @ElementCollection
    @CollectionTable(name = "rated_item_ids", joinColumns = @JoinColumn(name = "rating_id"))
    @Column(name = "item_id")
    private List<Long> itemIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "image", joinColumns = @JoinColumn(name = "rating_id"))
    @Column(name = "imageBase64", nullable = true)
    private List<String> imageBase64 = new ArrayList<>();



    private LocalDateTime created_at;

}
