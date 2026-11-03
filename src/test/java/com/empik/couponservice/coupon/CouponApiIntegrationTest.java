package com.empik.couponservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.empik.couponservice.TestcontainersConfiguration;
import com.empik.couponservice.geolocation.CountryResolver;

/**
 * End-to-end tests of the coupon API against a real PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CouponApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @MockitoBean
    private CountryResolver countryResolver;

    @BeforeEach
    void setUp() {
        couponUsageRepository.deleteAll();
        couponRepository.deleteAll();
        clientConnectsFrom("PL");
    }

    @Test
    void usingCouponRegistersUsageAndIncrementsCounter() {
        createCoupon("SPRING", 10, "PL");

        var result = useCoupon("spring", "Łukasz");

        assertThat(result).hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .isLenientlyEqualTo("""
                    { "code": "SPRING", "userId": "Łukasz" }
                    """);
        assertThat(result).bodyJson().extractingPath("$.usedAt").isNotNull();
        assertThat(mvc.get().uri("/api/v1/coupons/SPRING")).bodyJson()
            .extractingPath("$.currentUses")
            .isEqualTo(1);
    }

    @Test
    void userCannotUseCouponTwice() {
        createCoupon("SPRING", 10, "PL");
        useCoupon("SPRING", "Łukasz");

        assertProblem(useCoupon("SPRING", "Łukasz"), HttpStatus.CONFLICT, "COUPON_ALREADY_USED");
        assertCurrentUses("SPRING", 1);
    }

    @Test
    void couponCannotBeUsedFromAnotherCountry() {
        createCoupon("SPRING", 10, "PL");
        clientConnectsFrom("DE");

        assertProblem(useCoupon("SPRING", "Łukasz"), HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED");
        assertCurrentUses("SPRING", 0);
    }

    @Test
    void couponCannotBeUsedBeyondLimit() {
        createCoupon("SPRING", 1, "PL");
        useCoupon("SPRING", "Łukasz");

        assertProblem(useCoupon("SPRING", "Anna"), HttpStatus.CONFLICT, "COUPON_USAGE_LIMIT_REACHED");
        assertCurrentUses("SPRING", 1);
    }

    @Test
    void usingUnknownCouponReturnsNotFound() {
        assertProblem(useCoupon("UNKNOWN", "Łukasz"), HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
    }

    @Test
    void alreadyUsedTakesPrecedenceOverCountryAndLimit() {
        createCoupon("SPRING", 1, "PL");
        useCoupon("SPRING", "Łukasz");
        clientConnectsFrom("DE");

        assertProblem(useCoupon("SPRING", "Łukasz"), HttpStatus.CONFLICT, "COUPON_ALREADY_USED");
    }

    @Test
    void countryTakesPrecedenceOverLimit() {
        createCoupon("SPRING", 1, "PL");
        useCoupon("SPRING", "Łukasz");
        clientConnectsFrom("DE");

        assertProblem(useCoupon("SPRING", "Anna"), HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED");
    }

    @Test
    void createdCouponCanBeRetrievedByCodeInAnyCase() {
        var created = createCoupon("Spring", 100, "PL");

        assertThat(created).hasStatus(HttpStatus.CREATED)
            .hasHeader("Location", "http://localhost/api/v1/coupons/SPRING");
        assertThat(created).bodyJson().extractingPath("$.createdAt").isNotNull();

        assertThat(mvc.get().uri("/api/v1/coupons/spring")).hasStatus(HttpStatus.OK)
            .bodyJson()
            .isLenientlyEqualTo("""
                    { "code": "SPRING", "maxUses": 100, "currentUses": 0, "country": "PL" }
                    """);
    }

    @Test
    void couponCodeIsUniqueRegardlessOfCase() {
        assertThat(createCoupon("SPRING", 100, "PL")).hasStatus(HttpStatus.CREATED);

        var duplicate = createCoupon("spring", 5, "DE");

        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo("COUPON_CODE_ALREADY_EXISTS");
        assertThat(couponRepository.count()).isEqualTo(1);
    }

    @Test
    void gettingUnknownCouponReturnsNotFound() {
        assertThat(mvc.get().uri("/api/v1/coupons/UNKNOWN")).hasStatus(HttpStatus.NOT_FOUND)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo("COUPON_NOT_FOUND");
    }

    private MvcTestResult createCoupon(String code, int maxUses, String country) {
        return mvc.post()
            .uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    { "code": "%s", "maxUses": %d, "country": "%s" }
                    """.formatted(code, maxUses, country))
            .exchange();
    }

    private MvcTestResult useCoupon(String code, String userId) {
        return mvc.post()
            .uri("/api/v1/coupons/{code}/usages", code)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    { "userId": "%s" }
                    """.formatted(userId))
            .exchange();
    }

    private void clientConnectsFrom(String country) {
        given(countryResolver.resolveCountry(anyString())).willReturn(country);
    }

    private void assertCurrentUses(String code, int expected) {
        assertThat(couponRepository.findByCode(code)).get()
            .extracting(Coupon::getCurrentUses)
            .isEqualTo(expected);
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo(code);
    }

}
