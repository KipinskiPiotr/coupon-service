package com.empik.couponservice.geolocation;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl        base URL of the geolocation service
 * @param connectTimeout maximum time to establish a connection
 * @param readTimeout    maximum time to wait for the response once connected
 * @param cache          cache of resolved countries
 */
@ConfigurationProperties("geolocation")
record GeolocationProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, CacheProperties cache) {

    /**
     * @param ttl         how long a resolved country is kept
     * @param maximumSize maximum number of cached IP addresses
     */
    record CacheProperties(Duration ttl, long maximumSize) {
    }

}
