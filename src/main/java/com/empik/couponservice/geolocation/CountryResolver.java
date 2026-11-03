package com.empik.couponservice.geolocation;

/**
 * Resolves the country a client connects from.
 */
public interface CountryResolver {

    /**
     * @param ipAddress the client's IP address
     * @return upper-case ISO 3166-1 alpha-2 country code
     */
    String resolveCountry(String ipAddress);

}
