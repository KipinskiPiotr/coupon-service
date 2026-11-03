package com.empik.couponservice.geolocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CachingCountryResolverTest {

    private static final String IP = "203.0.113.10";

    private final CountryResolver delegate = mock(CountryResolver.class);
    private final CachingCountryResolver resolver = new CachingCountryResolver(delegate,
            new GeolocationProperties.CacheProperties(Duration.ofHours(1), 100));

    @Test
    void callsDelegateOncePerIpAddress() {
        given(delegate.resolveCountry(IP)).willReturn("PL");

        assertThat(resolver.resolveCountry(IP)).isEqualTo("PL");
        assertThat(resolver.resolveCountry(IP)).isEqualTo("PL");

        verify(delegate, times(1)).resolveCountry(IP);
    }

    @Test
    void doesNotCacheFailures() {
        given(delegate.resolveCountry(IP))
            .willThrow(new GeolocationUnavailableException("The geolocation service is unavailable"))
            .willReturn("PL");

        assertThatThrownBy(() -> resolver.resolveCountry(IP)).isInstanceOf(GeolocationUnavailableException.class);
        assertThat(resolver.resolveCountry(IP)).isEqualTo("PL");

        verify(delegate, times(2)).resolveCountry(IP);
    }

}
