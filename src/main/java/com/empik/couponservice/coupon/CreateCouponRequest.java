package com.empik.couponservice.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record CreateCouponRequest(
		@NotBlank
		@Size(max = 32)
		@Pattern(regexp = "[A-Za-z0-9_-]*", message = "may contain only letters, digits, '-' and '_'")
		String code,

		@NotNull
		@Positive
		Integer maxUses,

		@NotNull
		@IsoCountryCode
		String country) {
}
