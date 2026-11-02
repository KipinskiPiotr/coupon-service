package com.empik.couponservice.error;

/**
 * Base class for business errors returned to API clients as Problem Details.
 */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	protected ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

}
