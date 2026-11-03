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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupons")
@RestController
@RequestMapping("/api/v1/coupons")
class CouponController {

    private final CouponService couponService;

    CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "Create a coupon")
    @ApiResponse(responseCode = "201", description = "Coupon created",
            headers = @Header(name = "Location", description = "URL of the created coupon"))
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`: invalid request body")
    @ApiResponse(responseCode = "409",
            description = "`COUPON_CODE_ALREADY_EXISTS`: the code is taken (case-insensitive)")
    @PostMapping
    ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        Coupon coupon = couponService.createCoupon(request.code(), request.maxUses(), request.country());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{code}")
            .buildAndExpand(coupon.getCode())
            .toUri();
        return ResponseEntity.created(location).body(CouponResponse.from(coupon));
    }

    @Operation(summary = "Get a coupon", description = "The code is case-insensitive.")
    @ApiResponse(responseCode = "200", description = "Coupon found")
    @ApiResponse(responseCode = "404", description = "`COUPON_NOT_FOUND`")
    @GetMapping("/{code}")
    CouponResponse getCoupon(@PathVariable String code) {
        return CouponResponse.from(couponService.getCoupon(code));
    }

    @Operation(summary = "Use a coupon", description = """
            Registers a usage of the coupon by the user. The code is case-insensitive.

            When several errors apply, the first one in this order is returned: `COUPON_NOT_FOUND`, \
            `COUPON_ALREADY_USED`, `GEOLOCATION_UNAVAILABLE`, `COUNTRY_NOT_ALLOWED`, \
            `COUPON_USAGE_LIMIT_REACHED`.""")
    @Parameter(in = ParameterIn.HEADER, name = "X-Forwarded-For", example = "83.0.0.1",
            description = "Client IP address, honored only from a trusted proxy (e.g. Docker or a load balancer)")
    @ApiResponse(responseCode = "201", description = "Usage registered")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`: invalid request body")
    @ApiResponse(responseCode = "403",
            description = "`COUNTRY_NOT_ALLOWED`: the client is not in the coupon's country")
    @ApiResponse(responseCode = "404", description = "`COUPON_NOT_FOUND`")
    @ApiResponse(responseCode = "409", description = """
            `COUPON_ALREADY_USED`: the user has already used the coupon, or \
            `COUPON_USAGE_LIMIT_REACHED`: the coupon has no uses left""")
    @ApiResponse(responseCode = "503",
            description = "`GEOLOCATION_UNAVAILABLE`: the client's country cannot be determined")
    @PostMapping("/{code}/usages")
    ResponseEntity<CouponUsageResponse> useCoupon(@PathVariable String code,
            @Valid @RequestBody UseCouponRequest request, HttpServletRequest httpRequest) {
        CouponUsage usage = couponService.useCoupon(code, request.userId(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CouponUsageResponse.from(Coupon.normalizeCode(code), usage));
    }

}
