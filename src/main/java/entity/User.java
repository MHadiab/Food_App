package entity;

import jakarta.persistence.*; // Use jakarta.persistence instead of javax.persistence
import lombok.Getter;
import lombok.Setter;

import java.util.Date;         // For Date types
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

    @Column()
    private String profileImageBase64;

    @Embedded
    private BankInfo bank_info;
}
