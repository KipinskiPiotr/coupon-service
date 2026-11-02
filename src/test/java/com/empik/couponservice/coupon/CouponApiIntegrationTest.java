package com.empik.couponservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.empik.couponservice.TestcontainersConfiguration;

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

    @BeforeEach
    void cleanDatabase() {
        couponRepository.deleteAll();
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

}
