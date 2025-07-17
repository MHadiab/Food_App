package entity;

import jakarta.persistence.*; // Use jakarta.persistence instead of javax.persistence
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long user_id;

    @Column(nullable = false)
    private String full_name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String address;

    @Lob
    @Column(nullable = true)
    private String profileImageBase64;

    @Column(nullable = true)
    private Double balance=0.0;

    @Column(nullable = true)
    private UserStatus status;

    @Embedded
    private BankInfo bank_info;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_favorites",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "restaurant_id")
    )
    private Set<Restaurant> favorites = new HashSet<>();
}
