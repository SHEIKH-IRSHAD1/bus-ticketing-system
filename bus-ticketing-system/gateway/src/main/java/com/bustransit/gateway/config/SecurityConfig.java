package com.bustransit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * IMPORTANT - read before deploying this anywhere real:
 *
 * This gateway is wired up to validate HS256 JWTs against a shared secret
 * (see jwtDecoder() below), but the original system design has no user
 * login/signup flow that would actually *issue* those tokens to passengers,
 * drivers, kiosks, or conductors. Building that is a separate piece of work.
 *
 * Until that exists, every route below is permitAll() so the system is
 * actually usable end-to-end. Once you have something issuing JWTs (your own
 * auth service, or a provider like Auth0/Cognito/Keycloak - all have free
 * tiers), flip the commented block below to require authentication on the
 * routes that should no longer be public.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${gateway.jwt-secret}")
    private String jwtSecret;

    @Value("${gateway.cors-allowed-origin-patterns}")
    private String corsAllowedOriginPatterns;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        // --- Current state: everything is public. ---
                        .anyExchange().permitAll()

                        // --- Once you have a token issuer, replace the line above with
                        //     something like this to actually enforce auth: ---
                        // .pathMatchers("/api/tickets/purchase").authenticated()
                        // .pathMatchers("/api/tickets/*/validate").hasAuthority("SCOPE_conductor")
                        // .anyExchange().permitAll()
                )
                // Resource-server support is configured and ready to use the moment
                // enforcement is turned on above - no other wiring needed.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())));

        return http.build();
    }

    /**
     * The Passenger PWA, Kiosk and Conductor App run on different origins
     * (e.g. localhost:5173) than the gateway (localhost:8080), so browsers
     * enforce CORS on every call they make to it. Without this, the browser
     * blocks the requests before they ever reach a backend service.
     * Tighten the allowed origins to your real domains when you deploy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsAllowedOriginPatterns.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }
}
