package com.empik.couponservice.geolocation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeolocationProperties.class)
class GeolocationConfiguration {

    @Bean
    CountryResolver countryResolver(GeolocationProperties properties) {
        return new CachingCountryResolver(new IpapiCountryResolver(properties), properties.cache());
    }

}
