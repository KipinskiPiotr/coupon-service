package com.empik.couponservice.coupon;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

class CouponNotFoundException extends ApiException {

    CouponNotFoundException(String code) {
        super(ErrorCode.COUPON_NOT_FOUND, "Coupon %s does not exist".formatted(code));
    }

}
