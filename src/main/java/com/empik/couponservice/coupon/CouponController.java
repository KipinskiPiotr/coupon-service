package com.empik.couponservice.coupon;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController {

	@PostMapping
	ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
		throw notImplemented();
	}

	@GetMapping("/{code}")
	CouponResponse getCoupon(@PathVariable String code) {
		throw notImplemented();
	}

	@PostMapping("/{code}/usages")
	ResponseEntity<CouponUsageResponse> useCoupon(@PathVariable String code,
			@Valid @RequestBody UseCouponRequest request) {
		throw notImplemented();
	}

	// TODO: remove once all endpoints are implemented
	private static ResponseStatusException notImplemented() {
		return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
	}

}
