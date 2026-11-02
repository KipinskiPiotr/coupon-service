package com.empik.couponservice.coupon;

import java.time.Instant;

record CouponResponse(String code, int maxUses, int currentUses, String country, Instant createdAt) {
}
