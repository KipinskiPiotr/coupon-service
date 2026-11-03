package com.empik.couponservice.geolocation;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resolves countries with <a href="https://ipapi.co">ipapi.co</a>: no API key required, HTTPS, and the
 * {@code /{ip}/country/} endpoint returns just the country code as plain text.
 */
class IpapiCountryResolver implements CountryResolver {

    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    private final RestClient restClient;

    IpapiCountryResolver(GeolocationProperties properties) {
        // The JDK HttpClient has no timeouts by default: a hanging provider would block request threads
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, "coupon-service")
            .build();
    }

    @Override
    public String resolveCountry(String ipAddress) {
        // Private and loopback addresses have no country; asking the provider would only waste its quota
        if (!isPublic(ipAddress)) {
            throw new GeolocationUnavailableException("The client's IP address is not a public address");
        }
        String response;
        try {
            response = restClient.get().uri("/{ip}/country/", ipAddress).retrieve().body(String.class);
        }
        catch (RestClientException ex) {
            throw new GeolocationUnavailableException("The geolocation service is unavailable", ex);
        }
        // For unknown addresses the provider responds with "Undefined" and status 200
        String country = response == null ? "" : response.strip();
        if (!COUNTRY_CODE.matcher(country).matches()) {
            throw new GeolocationUnavailableException("The country of the client's IP address is unknown");
        }
        return country;
    }

    private static boolean isPublic(String ipAddress) {
        InetAddress address;
        try {
            // Parses the literal only, never performs a DNS lookup
            address = InetAddress.ofLiteral(ipAddress);
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
        return !(address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress());
    }

}
