package com.empik.couponservice.coupon;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class CouponService {

    private static final String UNIQUE_CODE_CONSTRAINT = "uq_coupon_code";

    private final CouponRepository couponRepository;

    CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    Coupon createCoupon(String code, int maxUses, String country) {
        String normalizedCode = normalize(code);
        // No "exists" check up front: two concurrent requests could both pass it.
        // The unique constraint is the single source of truth.
        try {
            return couponRepository.saveAndFlush(new Coupon(normalizedCode, maxUses, country));
        }
        catch (DataIntegrityViolationException ex) {
            if (violates(ex, UNIQUE_CODE_CONSTRAINT)) {
                throw new CouponCodeAlreadyExistsException(normalizedCode);
            }
            throw ex;
        }
    }

    Coupon getCoupon(String code) {
        String normalizedCode = normalize(code);
        return couponRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new CouponNotFoundException(normalizedCode));
    }

    // Locale.ROOT: upper-casing must not depend on the server's locale
    private static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        return ex.getCause() instanceof ConstraintViolationException cause
                && constraintName.equalsIgnoreCase(cause.getConstraintName());
    }

}
