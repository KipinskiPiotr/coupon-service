package com.empik.couponservice.coupon;

import java.time.Instant;

record CouponResponse(String code, int maxUses, int currentUses, String country, Instant createdAt) {

    static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getCode(), coupon.getMaxUses(), coupon.getCurrentUses(),
                coupon.getCountry(), coupon.getCreatedAt());
    }

}
