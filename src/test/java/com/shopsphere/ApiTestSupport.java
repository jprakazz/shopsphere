package com.shopsphere;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Boots the whole application against in-memory H2 and drives it over HTTP with MockMvc.
 * Every helper creates uniquely named data, so tests never depend on each other's state.
 */
@SpringBootTest(properties = "app.orders.expiry-job-enabled=false")
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected String body(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        return mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    protected static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    protected static long readLong(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    protected String registerCustomer() throws Exception {
        String email = unique("user").toLowerCase() + "@example.com";
        String res = body(json(post("/api/auth/register"),
                "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"), 201);
        return read(res, "$.accessToken");
    }

    protected String adminToken() throws Exception {
        String res = body(json(post("/api/auth/login"),
                "{\"email\":\"admin@shopsphere.dev\",\"password\":\"Admin@123\"}"), 200);
        return read(res, "$.accessToken");
    }

    protected long createCategory(String admin) throws Exception {
        String res = body(auth(json(post("/api/categories"),
                "{\"name\":\"" + unique("Cat") + "\",\"description\":\"test\"}"), admin), 201);
        return readLong(res, "$.id");
    }

    protected long createProduct(String admin, long categoryId, String price, int stock) throws Exception {
        String res = body(auth(json(post("/api/products"), """
                {"sku":"%s","name":"%s","description":"test product","price":%s,"stockQuantity":%d,"categoryId":%d}
                """.formatted(unique("SKU"), unique("Widget"), price, stock, categoryId)), admin), 201);
        return readLong(res, "$.id");
    }

    protected long createAddress(String token) throws Exception {
        String res = body(auth(json(post("/api/addresses"), """
                {"line1":"12 MG Road","city":"Bengaluru","state":"Karnataka","postalCode":"560001","country":"India"}
                """), token), 201);
        return readLong(res, "$.id");
    }

    protected void addToCart(String token, long productId, int quantity) throws Exception {
        body(auth(json(post("/api/cart/items"),
                "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"), token), 200);
    }

    protected long checkout(String token, long addressId) throws Exception {
        String res = body(auth(json(post("/api/orders"), "{\"addressId\":" + addressId + "}"), token), 201);
        return readLong(res, "$.id");
    }

    protected int stockOf(long productId) throws Exception {
        String res = body(get("/api/products/" + productId), 200);
        return read(res, "$.stockQuantity");
    }
}
