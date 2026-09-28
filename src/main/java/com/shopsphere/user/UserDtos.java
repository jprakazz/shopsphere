package com.shopsphere.user;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "is not a valid phone number") String phone) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
    }

    public record UserResponse(Long id, String fullName, String email, String phone, Role role,
                               LocalDateTime createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                    u.getCreatedAt());
        }
    }

    public record AddressRequest(
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "is not a valid postal code") String postalCode,
            @NotBlank @Size(max = 60) String country,
            Boolean makeDefault) {
    }

    public record AddressResponse(Long id, String line1, String line2, String city, String state,
                                  String postalCode, String country, boolean isDefault) {
        public static AddressResponse from(Address a) {
            return new AddressResponse(a.getId(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                    a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
        }
    }
}
