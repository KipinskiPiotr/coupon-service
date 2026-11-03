package com.empik.couponservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.geolocation.GeolocationUnavailableException;

/**
 * Web layer only: request validation, status codes and response format. The service is mocked.
 */
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CouponService couponService;

    @Test
    void createCouponReturnsCreatedCouponWithLocation() {
        given(couponService.createCoupon("Spring", 100, "PL")).willReturn(new Coupon("SPRING", 100, "PL"));

        var result = post("/api/v1/coupons", """
                { "code": "Spring", "maxUses": 100, "country": "PL" }
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED)
            .hasHeader(HttpHeaders.LOCATION, "http://localhost/api/v1/coupons/SPRING")
            .bodyJson()
            .isLenientlyEqualTo("""
                    { "code": "SPRING", "maxUses": 100, "currentUses": 0, "country": "PL" }
                    """);
    }

    @Test
    void createCouponReturnsConflictWhenCodeExists() {
        given(couponService.createCoupon(anyString(), anyInt(), anyString()))
            .willThrow(new CouponCodeAlreadyExistsException("SPRING"));

        var result = post("/api/v1/coupons", """
                { "code": "Spring", "maxUses": 100, "country": "PL" }
                """);

        assertProblem(result, HttpStatus.CONFLICT, "COUPON_CODE_ALREADY_EXISTS");
    }

    @Test
    void getCouponReturnsCoupon() {
        given(couponService.getCoupon("spring")).willReturn(new Coupon("SPRING", 100, "PL"));

        assertThat(mvc.get().uri("/api/v1/coupons/spring")).hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo("SPRING");
    }

    @Test
    void getCouponReturnsNotFoundWhenCouponDoesNotExist() {
        given(couponService.getCoupon("SPRING")).willThrow(new CouponNotFoundException("SPRING"));

        assertProblem(mvc.get().uri("/api/v1/coupons/SPRING").exchange(), HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
    }

    @Test
    void unexpectedErrorReturnsProblemDetailWithoutInternals() {
        given(couponService.getCoupon("SPRING")).willThrow(new IllegalStateException("connection refused"));

        var result = mvc.get().uri("/api/v1/coupons/SPRING").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .isLenientlyEqualTo("""
                    { "status": 500, "detail": "An unexpected error occurred" }
                    """);
        assertThat(result).bodyText().doesNotContain("connection refused");
    }

    @Test
    void createCouponRejectsMissingFields() {
        var result = post("/api/v1/coupons", "{}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson()
            .extractingPath("$.errors[*].field")
            .asArray()
            .containsExactlyInAnyOrder("code", "maxUses", "country");
    }

    @ParameterizedTest
    @ValueSource(strings = { "SPRING SALE", "ŻÓŁW", "A23456789012345678901234567890123" })
    void createCouponRejectsInvalidCode(String code) {
        var result = post("/api/v1/coupons", """
                { "code": "%s", "maxUses": 100, "country": "PL" }
                """.formatted(code));

        assertValidationError(result, "code");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void createCouponRejectsNonPositiveMaxUses(int maxUses) {
        var result = post("/api/v1/coupons", """
                { "code": "SPRING", "maxUses": %d, "country": "PL" }
                """.formatted(maxUses));

        assertValidationError(result, "maxUses");
    }

    @ParameterizedTest
    @ValueSource(strings = { "XX", "pl", "POL", "" })
    void createCouponRejectsInvalidCountry(String country) {
        var result = post("/api/v1/coupons", """
                { "code": "SPRING", "maxUses": 100, "country": "%s" }
                """.formatted(country));

        assertValidationError(result, "country");
    }

    @Test
    void useCouponPassesConnectionIpAndReturnsCreatedUsage() {
        given(couponService.useCoupon("spring", "Łukasz", "127.0.0.1"))
            .willReturn(new CouponUsage(1L, "Łukasz", "PL"));

        var result = post("/api/v1/coupons/spring/usages", """
                { "userId": "Łukasz" }
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED)
            .doesNotContainHeader(HttpHeaders.LOCATION)
            .bodyJson()
            .isLenientlyEqualTo("""
                    { "code": "SPRING", "userId": "Łukasz" }
                    """);
    }

    @ParameterizedTest
    @MethodSource("usageErrors")
    void useCouponMapsBusinessErrorsToProblemDetails(ApiException exception, HttpStatus status, String code) {
        given(couponService.useCoupon(anyString(), anyString(), anyString())).willThrow(exception);

        var result = post("/api/v1/coupons/SPRING/usages", """
                { "userId": "Łukasz" }
                """);

        assertProblem(result, status, code);
    }

    static Stream<Arguments> usageErrors() {
        return Stream.of(
                Arguments.of(new CouponNotFoundException("SPRING"), HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND"),
                Arguments.of(new CouponAlreadyUsedException("SPRING"), HttpStatus.CONFLICT, "COUPON_ALREADY_USED"),
                Arguments.of(new CountryNotAllowedException("SPRING", "DE"), HttpStatus.FORBIDDEN,
                        "COUNTRY_NOT_ALLOWED"),
                Arguments.of(new CouponUsageLimitReachedException("SPRING"), HttpStatus.CONFLICT,
                        "COUPON_USAGE_LIMIT_REACHED"),
                Arguments.of(new GeolocationUnavailableException("The geolocation service is unavailable"),
                        HttpStatus.SERVICE_UNAVAILABLE, "GEOLOCATION_UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "1234567890123456789012345678901234567" })
    void useCouponRejectsInvalidUserId(String userId) {
        var result = post("/api/v1/coupons/SPRING/usages", """
                { "userId": "%s" }
                """.formatted(userId));

        assertValidationError(result, "userId");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private void assertValidationError(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.errors[*].field")
            .asArray()
            .containsExactly(field);
        verifyNoInteractions(couponService);
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo(code);
    }

}
