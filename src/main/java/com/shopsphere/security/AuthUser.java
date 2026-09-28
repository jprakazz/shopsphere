package com.shopsphere.security;

import com.shopsphere.user.Role;

/** The authenticated caller, rebuilt from the JWT on every request (no session, no DB lookup). */
public record AuthUser(Long id, String email, Role role) {
}
