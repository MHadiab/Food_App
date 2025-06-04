package response;

import entity.Coupon;
import entity.CouponType;
import lombok.Getter;
import lombok.Setter;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
public class CouponResponse {

    private Integer id;
    private String couponCode;
    private String type;
    private Double value;
    private Integer minPrice;
    private Integer userCount;
    private String startDate;
    private String endDate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public CouponResponse(Coupon coupon) {
        this.id = coupon.getId();
        this.couponCode = coupon.getCouponCode();
        if (coupon.getType() != null) {
            this.type = coupon.getType().name();
        }
        this.value = coupon.getValue();
        this.minPrice = coupon.getMinPrice();
        this.userCount = coupon.getUserCount();
        if (coupon.getStartDate() != null) {
            this.startDate = coupon.getStartDate().format(DATE_FORMATTER);
        }
        if (coupon.getEndDate() != null) {
            this.endDate = coupon.getEndDate().format(DATE_FORMATTER);
        }
    }
}