package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String logoBase64;

    @Column(nullable = false)
    private Integer tax_fee;

    @Column(nullable = false)
    private Integer additional_fee;

    @Column(nullable = false)
    private long seller_id;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true;   // برای رستوارن اکتیو گذاشتم میتونیم برش داریم در صورت استفاده نشدن

}