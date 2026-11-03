package com.empik.couponservice.coupon;

import java.time.Instant;

record CouponUsageResponse(String code, String userId, Instant usedAt) {

    static CouponUsageResponse from(String couponCode, CouponUsage usage) {
        return new CouponUsageResponse(couponCode, usage.getUserId(), usage.getUsedAt());
    }

}
