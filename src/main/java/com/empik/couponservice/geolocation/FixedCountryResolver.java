package com.empik.couponservice.geolocation;

import org.springframework.stereotype.Component;

/**
 * Placeholder until geolocation by IP address is implemented: every client is resolved to Poland.
 */
// TODO: replace with a resolver backed by a geolocation service
@Component
class FixedCountryResolver implements CountryResolver {

    private static final String COUNTRY = "PL";

    @Override
    public String resolveCountry(String ipAddress) {
        return COUNTRY;
    }

}
