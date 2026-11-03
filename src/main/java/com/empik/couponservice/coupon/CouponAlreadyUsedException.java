package com.empik.couponservice.coupon;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

class CouponAlreadyUsedException extends ApiException {

    CouponAlreadyUsedException(String code) {
        super(ErrorCode.COUPON_ALREADY_USED, "Coupon %s has already been used by this user".formatted(code));
    }

}
