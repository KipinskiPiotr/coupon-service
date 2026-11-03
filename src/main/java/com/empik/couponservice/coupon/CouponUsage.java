package com.empik.couponservice.coupon;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;

/**
 * A single use of a coupon. Records are append-only, so all columns are immutable.
 */
@Entity
@Table(name = "coupon_usage")
class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain ID instead of a @ManyToOne: the usage log never navigates to the coupon
    @Column(nullable = false, updatable = false)
    private Long couponId;

    @Column(nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, updatable = false)
    private String country;

    @Generated
    @Column(nullable = false, insertable = false, updatable = false)
    private Instant usedAt;

    protected CouponUsage() {
        // required by JPA
    }

    CouponUsage(Long couponId, String userId, String country) {
        this.couponId = couponId;
        this.userId = userId;
        this.country = country;
    }

    String getUserId() {
        return userId;
    }

    String getCountry() {
        return country;
    }

    Instant getUsedAt() {
        return usedAt;
    }

}
