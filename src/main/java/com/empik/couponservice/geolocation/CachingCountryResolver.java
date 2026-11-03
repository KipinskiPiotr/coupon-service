package com.empik.couponservice.geolocation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caches resolved countries in memory, so repeated requests from the same client do not call the
 * provider again. Only successful lookups are cached: a failure is retried on the next request.
 * Entries expire after the TTL, as IP addresses get reassigned and are personal data.
 */
class CachingCountryResolver implements CountryResolver {

    private final CountryResolver delegate;
    private final Cache<String, String> cache;

    CachingCountryResolver(CountryResolver delegate, GeolocationProperties.CacheProperties properties) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(properties.ttl())
            .maximumSize(properties.maximumSize())
            .build();
    }

    @Override
    public String resolveCountry(String ipAddress) {
        // If the delegate throws, nothing is cached and the exception propagates
        return cache.get(ipAddress, delegate::resolveCountry);
    }

}
