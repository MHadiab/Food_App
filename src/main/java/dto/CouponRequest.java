package dto;

import entity.Coupon;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponRequest {

    private String couponCode;

    private String type;

    private Double value;

    private Integer minPrice;

    private Integer userCount;

    private String startDate;

    private String endDate;

    public CouponRequest() {}
}