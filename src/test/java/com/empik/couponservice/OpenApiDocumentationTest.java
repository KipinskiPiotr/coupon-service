package com.empik.couponservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.empik.couponservice.geolocation.CountryResolver;

/**
 * The OpenAPI document is built only when requested, so this test makes sure building it works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationTest {

    private static final String USAGES = "$.paths['/api/v1/coupons/{code}/usages'].post.responses";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CountryResolver countryResolver;

    @Test
    void documentsSuccessAndErrorResponses() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson()
            .extractingPath(USAGES + "['201'].content['application/json'].schema.$ref")
            .isEqualTo("#/components/schemas/CouponUsageResponse");
        assertThat(result).bodyJson()
            .extractingPath(USAGES + "['503'].content['application/problem+json'].schema.$ref")
            .isEqualTo("#/components/schemas/ProblemDetail");
    }

}
