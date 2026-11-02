package com.empik.couponservice.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	COUNTRY_NOT_ALLOWED(HttpStatus.FORBIDDEN),
	COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
	COUPON_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT),
	COUPON_USAGE_LIMIT_REACHED(HttpStatus.CONFLICT),
	COUPON_ALREADY_USED(HttpStatus.CONFLICT);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
