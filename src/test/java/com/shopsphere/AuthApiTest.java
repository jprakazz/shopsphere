package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registerLoginAndFetchProfile() throws Exception {
        String email = unique("asha").toLowerCase() + "@example.com";
        String reg = body(json(post("/api/auth/register"),
                "{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"), 201);
        assertThat(read(reg, "$.tokenType").toString()).isEqualTo("Bearer");
        assertThat(read(reg, "$.user.role").toString()).isEqualTo("CUSTOMER");

        String login = body(json(post("/api/auth/login"),
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Secret@123\"}"), 200);
        String me = body(auth(get("/api/auth/me"), read(login, "$.accessToken")), 200);
        assertThat(read(me, "$.email").toString()).isEqualTo(email);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = unique("dup").toLowerCase() + "@example.com";
        String req = "{\"fullName\":\"A\",\"email\":\"%s\",\"password\":\"Secret@123\"}";
        body(json(post("/api/auth/register"), req.formatted(email)), 201);
        body(json(post("/api/auth/register"), req.formatted(email.toUpperCase())), 409);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String email = unique("pw").toLowerCase() + "@example.com";
        body(json(post("/api/auth/register"),
                "{\"fullName\":\"A\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"), 201);
        String wrong = body(json(post("/api/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"nope-nope\"}"), 401);
        String unknown = body(json(post("/api/auth/login"),
                "{\"email\":\"nobody@example.com\",\"password\":\"nope-nope\"}"), 401);
        assertThat(read(wrong, "$.detail").toString()).isEqualTo(read(unknown, "$.detail").toString());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        body(get("/api/cart"), 401);
        body(auth(get("/api/cart"), "not-a-jwt"), 401);
    }

    @Test
    void invalidRegistrationListsEveryBadField() throws Exception {
        String res = body(json(post("/api/auth/register"),
                "{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"short\"}"), 400);
        assertThat(read(res, "$.errors").toString()).contains("fullName", "email", "password");
    }
}
