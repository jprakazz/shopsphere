package com.shopsphere.user;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.common.NotFoundException;
import com.shopsphere.security.JwtService;
import com.shopsphere.user.UserDtos.AuthResponse;
import com.shopsphere.user.UserDtos.LoginRequest;
import com.shopsphere.user.UserDtos.RegisterRequest;
import com.shopsphere.user.UserDtos.UserResponse;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        User user = users.save(new User(req.fullName().trim(), email,
                passwordEncoder.encode(req.password()), req.phone(), Role.CUSTOMER));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                // Same message for unknown email and wrong password, so accounts cannot be enumerated.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.ttlSeconds(), UserResponse.from(user));
    }
}
