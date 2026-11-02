package com.empik.couponservice.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record UseCouponRequest(
		@NotBlank
		@Size(max = 36)
		String userId) {
}
