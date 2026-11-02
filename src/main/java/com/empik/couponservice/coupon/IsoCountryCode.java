package com.empik.couponservice.coupon;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must be an upper-case ISO 3166-1 alpha-2 country code. {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = IsoCountryCodeValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@interface IsoCountryCode {

    String message() default "must be an ISO 3166-1 alpha-2 country code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
