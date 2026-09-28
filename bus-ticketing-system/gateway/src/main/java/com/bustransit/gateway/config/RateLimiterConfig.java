package com.bustransit.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Rate-limits by client IP address. Good enough for a prototype; once you
     * have authenticated users, resolving by user ID (extracted from the JWT)
     * is usually the better key, since it survives shared/rotating IPs (e.g.
     * mobile carriers, corporate NAT) without one user's traffic penalizing others.
     */
    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }
}
