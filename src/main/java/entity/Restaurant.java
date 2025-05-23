package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String logoBase64;

    @Column(nullable = false)
    private Integer tax_fee;

    @Column(nullable = false)
    private Integer additional_fee;

    @Column(nullable = false)
    private long seller_id;

}