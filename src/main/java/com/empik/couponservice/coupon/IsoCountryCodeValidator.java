package com.empik.couponservice.coupon;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class IsoCountryCodeValidator implements ConstraintValidator<IsoCountryCode, String> {

	private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || ISO_COUNTRIES.contains(value);
	}

}
