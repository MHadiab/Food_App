package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TransactionType method;
    private double amount;
    private LocalDateTime date;
    private TransactionStatus status;
    @ManyToOne private User user;
    @OneToOne private Order order;
    private String description;
}
