package com.empik.couponservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.empik.couponservice.TestcontainersConfiguration;
import com.empik.couponservice.error.ApiException;
import com.empik.couponservice.geolocation.CountryResolver;

/**
 * Fires many usages of the same coupon at once. Most of them pass the read-only checks together, so
 * only the transaction (unique constraint and conditional update) can keep the result correct.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CouponUsageConcurrencyTest {

    private static final int THREADS = 32;
    private static final String SUCCESS = "SUCCESS";

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
        given(countryResolver.resolveCountry(anyString())).willReturn("PL");
    }

    @Test
    void usageLimitIsNeverExceeded() throws Exception {
        Coupon coupon = couponService.createCoupon("SPRING", 10, "PL");

        Map<String, Long> outcomes = useConcurrently(100, attempt -> "user-" + attempt);

        assertThat(outcomes).containsOnly(Map.entry(SUCCESS, 10L), Map.entry("COUPON_USAGE_LIMIT_REACHED", 90L));
        assertThat(couponService.getCoupon("SPRING").getCurrentUses()).isEqualTo(10);
        assertThat(couponUsageRepository.countByCouponId(coupon.getId())).isEqualTo(10);
    }

    @Test
    void userCanUseCouponOnlyOnceEvenWithConcurrentRequests() throws Exception {
        Coupon coupon = couponService.createCoupon("SPRING", 10, "PL");

        Map<String, Long> outcomes = useConcurrently(20, attempt -> "Łukasz");

        assertThat(outcomes).containsOnly(Map.entry(SUCCESS, 1L), Map.entry("COUPON_ALREADY_USED", 19L));
        assertThat(couponService.getCoupon("SPRING").getCurrentUses()).isEqualTo(1);
        assertThat(couponUsageRepository.countByCouponId(coupon.getId())).isEqualTo(1);
    }

    /**
     * Runs the attempts on a thread pool, all released at the same moment.
     *
     * @return the number of attempts per outcome: {@value #SUCCESS} or the error code
     */
    private Map<String, Long> useConcurrently(int attempts, Function<Integer, String> userIdForAttempt)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<String>> results = IntStream.range(0, attempts)
                .mapToObj(attempt -> executor.submit(() -> {
                    start.await();
                    return tryUse(userIdForAttempt.apply(attempt));
                }))
                .toList();
            start.countDown();

            return results.stream()
                .map(CouponUsageConcurrencyTest::join)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }
    }

    private String tryUse(String userId) {
        try {
            couponService.useCoupon("SPRING", userId, "127.0.0.1");
            return SUCCESS;
        }
        catch (ApiException ex) {
            return ex.errorCode().name();
        }
    }

    private static String join(Future<String> future) {
        try {
            return future.get();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Usage attempt failed unexpectedly", ex);
        }
    }

}
