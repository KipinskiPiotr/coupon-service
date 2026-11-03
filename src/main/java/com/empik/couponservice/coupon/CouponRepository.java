package com.empik.couponservice.coupon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    /**
     * Atomically increments the usage counter unless the limit has been reached. Concurrent calls wait
     * for the row lock and re-evaluate the condition, so the limit can never be exceeded.
     *
     * @return 1 if the counter was incremented, 0 if the limit has been reached
     */
    @Modifying
    @Query("update Coupon c set c.currentUses = c.currentUses + 1 where c.id = :id and c.currentUses < c.maxUses")
    int incrementUsesIfBelowLimit(@Param("id") Long id);

}
