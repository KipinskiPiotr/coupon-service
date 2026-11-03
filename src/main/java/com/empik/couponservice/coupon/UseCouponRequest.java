package com.empik.couponservice.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

record UseCouponRequest(
        @Schema(example = "lukasz")
        @NotBlank
        @Size(max = 36)
        String userId) {
}
