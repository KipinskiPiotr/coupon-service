package com.empik.couponservice.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, String userId);

    long countByCouponId(Long couponId);

}
