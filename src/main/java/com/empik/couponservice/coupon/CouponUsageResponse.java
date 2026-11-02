package com.empik.couponservice.coupon;

import java.time.Instant;

record CouponUsageResponse(String code, String userId, Instant usedAt) {
}
