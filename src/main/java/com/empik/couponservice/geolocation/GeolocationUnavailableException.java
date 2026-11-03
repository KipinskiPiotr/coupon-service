package com.empik.couponservice.geolocation;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.error.ErrorCode;

/**
 * The client's country could not be determined. Coupon usage is rejected in that case (fail closed),
 * otherwise a geolocation failure would bypass the country restriction.
 */
public class GeolocationUnavailableException extends ApiException {

    public GeolocationUnavailableException(String message) {
        super(ErrorCode.GEOLOCATION_UNAVAILABLE, message);
    }

    GeolocationUnavailableException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }

}
