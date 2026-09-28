package com.shopsphere.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, boolean seedSampleData, Kafka kafka) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Admin(String email, String password) {
    }

    public record Kafka(boolean enabled, String orderTopic) {
    }
}
