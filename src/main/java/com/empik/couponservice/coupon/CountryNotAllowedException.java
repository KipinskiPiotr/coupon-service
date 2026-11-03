package com.empik.couponservice.coupon;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

class CountryNotAllowedException extends ApiException {

    CountryNotAllowedException(String code, String country) {
        super(ErrorCode.COUNTRY_NOT_ALLOWED, "Coupon %s is not available in %s".formatted(code, country));
    }

}
