package com.shopsphere.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shopsphere.config.AppProperties;
import com.shopsphere.user.Role;
import com.shopsphere.user.User;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private static JwtService service(String secret, long minutes) {
        return new JwtService(new AppProperties(new AppProperties.Jwt(secret, minutes), null, false, null));
    }

    private static User user() {
        User u = new User("Asha", "asha@example.com", "hash", null, Role.ADMIN);
        ReflectionTestUtils.setField(u, "id", 42L);
        return u;
    }

    @Test
    void roundTripsTheCaller() {
        JwtService jwt = service(SECRET, 5);
        AuthUser caller = jwt.verify(jwt.issue(user())).orElseThrow();
        assertThat(caller).isEqualTo(new AuthUser(42L, "asha@example.com", Role.ADMIN));
    }

    @Test
    void rejectsTamperedTokens() {
        JwtService jwt = service(SECRET, 5);
        String token = jwt.issue(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(jwt.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String token = service("another-secret-another-secret-0123456789", 5).issue(user());
        assertThat(service(SECRET, 5).verify(token)).isEmpty();
    }

    @Test
    void rejectsExpiredTokens() {
        JwtService jwt = service(SECRET, -1);
        assertThat(jwt.verify(jwt.issue(user()))).isEmpty();
    }

    @Test
    void refusesWeakSecrets() {
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
    }
}
