package com.empik.couponservice.coupon;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.empik.couponservice.TestcontainersConfiguration;
import com.empik.couponservice.geolocation.CountryResolver;

/**
 * Runs on a real server: X-Forwarded-For is handled by Tomcat, which MockMvc bypasses.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ClientIpIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private CouponService couponService;

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
        couponService.createCoupon("SPRING", 10, "PL");
        given(countryResolver.resolveCountry(anyString())).willReturn("PL");
    }

    @Test
    void forwardedClientIpFromTrustedProxyIsUsedForGeolocation() {
        // The test client connects from localhost, which Tomcat trusts as an internal proxy
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/coupons/SPRING/usages")
            .header("X-Forwarded-For", "203.0.113.10")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                    { "userId": "Łukasz" }
                    """)
            .retrieve()
            .toBodilessEntity();

        verify(countryResolver).resolveCountry("203.0.113.10");
    }

}
