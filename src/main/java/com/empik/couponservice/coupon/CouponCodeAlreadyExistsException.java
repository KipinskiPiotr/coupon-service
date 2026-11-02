package com.empik.couponservice.coupon;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

class CouponCodeAlreadyExistsException extends ApiException {

    CouponCodeAlreadyExistsException(String code) {
        super(ErrorCode.COUPON_CODE_ALREADY_EXISTS, "Coupon %s already exists".formatted(code));
    }

}
