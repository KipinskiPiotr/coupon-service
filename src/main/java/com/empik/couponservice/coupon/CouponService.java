package com.empik.couponservice.coupon;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.empik.couponservice.geolocation.CountryResolver;

@Service
class CouponService {

    private static final String UNIQUE_CODE_CONSTRAINT = "uq_coupon_code";
    private static final String UNIQUE_USAGE_CONSTRAINT = "uq_coupon_usage_coupon_user";

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CountryResolver countryResolver;
    private final TransactionTemplate transactionTemplate;

    CouponService(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository,
            CountryResolver countryResolver, TransactionTemplate transactionTemplate) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.countryResolver = countryResolver;
        this.transactionTemplate = transactionTemplate;
    }

    Coupon createCoupon(String code, int maxUses, String country) {
        String normalizedCode = Coupon.normalizeCode(code);
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
        String normalizedCode = Coupon.normalizeCode(code);
        return couponRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new CouponNotFoundException(normalizedCode));
    }

    /**
     * Registers a usage of the coupon. Read-only checks run first, in the order of error precedence,
     * so most rejected requests never write. The transaction then makes the final decision and catches
     * races with concurrent requests. See design decision 20 in the README.
     */
    CouponUsage useCoupon(String code, String userId, String clientIp) {
        Coupon coupon = getCoupon(code);
        if (couponUsageRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new CouponAlreadyUsedException(coupon.getCode());
        }
        // Resolved outside the transaction: no database connection is held during an external call
        String clientCountry = countryResolver.resolveCountry(clientIp);
        if (!coupon.getCountry().equals(clientCountry)) {
            throw new CountryNotAllowedException(coupon.getCode(), clientCountry);
        }
        if (coupon.hasReachedLimit()) {
            throw new CouponUsageLimitReachedException(coupon.getCode());
        }
        return transactionTemplate.execute(status -> registerUsage(coupon, userId, clientCountry));
    }

    // Insert first, then update: the row lock on the coupon, the contention point, is held only
    // until the commit right after, and every transaction takes its locks in the same order.
    private CouponUsage registerUsage(Coupon coupon, String userId, String country) {
        CouponUsage usage;
        try {
            usage = couponUsageRepository.saveAndFlush(new CouponUsage(coupon.getId(), userId, country));
        }
        catch (DataIntegrityViolationException ex) {
            if (violates(ex, UNIQUE_USAGE_CONSTRAINT)) {
                throw new CouponAlreadyUsedException(coupon.getCode());
            }
            throw ex;
        }
        if (couponRepository.incrementUsesIfBelowLimit(coupon.getId()) == 0) {
            // Rolls back the usage inserted above
            throw new CouponUsageLimitReachedException(coupon.getCode());
        }
        return usage;
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        return ex.getCause() instanceof ConstraintViolationException cause
                && constraintName.equalsIgnoreCase(cause.getConstraintName());
    }

}
