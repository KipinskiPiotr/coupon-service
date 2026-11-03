package com.empik.couponservice.coupon;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;

@Entity
@Table(name = "coupon")
class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false, updatable = false)
    private int maxUses;

    @Column(nullable = false)
    private int currentUses;

    @Column(nullable = false, updatable = false)
    private String country;

    // Set by the database default, so all instances share one clock
    @Generated
    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Coupon() {
        // required by JPA
    }

    Coupon(String code, int maxUses, String country) {
        this.code = code;
        this.maxUses = maxUses;
        this.country = country;
        this.currentUses = 0;
    }

    /**
     * Codes are case-insensitive and stored in upper case. {@link Locale#ROOT} makes the result
     * independent of the server's locale.
     */
    static String normalizeCode(String code) {
        return code.toUpperCase(Locale.ROOT);
    }

    boolean hasReachedLimit() {
        return currentUses >= maxUses;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    int getMaxUses() {
        return maxUses;
    }

    int getCurrentUses() {
        return currentUses;
    }

    String getCountry() {
        return country;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

}
