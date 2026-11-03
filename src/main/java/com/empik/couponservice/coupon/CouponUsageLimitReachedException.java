package com.empik.couponservice.coupon;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

class CouponUsageLimitReachedException extends ApiException {

    CouponUsageLimitReachedException(String code) {
        super(ErrorCode.COUPON_USAGE_LIMIT_REACHED, "Coupon %s has reached its usage limit".formatted(code));
    }

}
