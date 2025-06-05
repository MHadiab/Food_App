package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "coupons")
@Getter
@Setter
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "coupon_code", unique = true, nullable = false)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Double value; // مقدار تخفیف

    @Column(name = "min_price", nullable = false)
    private Integer minPrice; // حداقل خرید برای اعمال کوپن

    @Column(name = "user_count", nullable = false)
    private Integer userCount; // تعداد دفعات قابل استفاده

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true; // فیلد اضافه برای کنترل کردن فعال بودن یا نبودن کوپن ها

    @Column(name = "times_used", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer timesUsed = 0; // تعداد دفعاتی که این کوپن استفاده شده


    // این متد رو باید وقتی سفارشی با استفاده از کوپن با موفقیت کامل شد صدا بزنی
    public void incrementTimesUsed() {
        if (this.timesUsed == null) {
            this.timesUsed = 0;
        }
        this.timesUsed++;

        //  اگر به حد استفاده رسید غیرفعالش میکنیم
        if (this.timesUsed >= this.userCount) {
            this.active = false;
        }
    }

    public Coupon() {}
}