package com.empik.couponservice.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

record CreateCouponRequest(
        @Schema(description = "Unique, case-insensitive; stored in upper case", example = "SPRING")
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "[A-Za-z0-9_-]*", message = "may contain only letters, digits, '-' and '_'")
        String code,

        @Schema(example = "100")
        @NotNull
        @Positive
        Integer maxUses,

        @Schema(description = "ISO 3166-1 alpha-2 country code, upper case", example = "PL")
        @NotNull
        @IsoCountryCode
        String country) {
}
