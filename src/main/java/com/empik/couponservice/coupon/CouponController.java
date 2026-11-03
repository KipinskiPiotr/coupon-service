package com.empik.couponservice.coupon;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController {

    private final CouponService couponService;

    CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        Coupon coupon = couponService.createCoupon(request.code(), request.maxUses(), request.country());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{code}")
            .buildAndExpand(coupon.getCode())
            .toUri();
        return ResponseEntity.created(location).body(CouponResponse.from(coupon));
    }

    @GetMapping("/{code}")
    CouponResponse getCoupon(@PathVariable String code) {
        return CouponResponse.from(couponService.getCoupon(code));
    }

    @PostMapping("/{code}/usages")
    ResponseEntity<CouponUsageResponse> useCoupon(@PathVariable String code,
            @Valid @RequestBody UseCouponRequest request, HttpServletRequest httpRequest) {
        CouponUsage usage = couponService.useCoupon(code, request.userId(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CouponUsageResponse.from(Coupon.normalizeCode(code), usage));
    }

}
