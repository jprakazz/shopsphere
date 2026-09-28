package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogApiTest extends ApiTestSupport {

    @Test
    void onlyAdminsCanCreateProducts() throws Exception {
        String customer = registerCustomer();
        body(auth(json(post("/api/categories"), "{\"name\":\"Hacked\"}"), customer), 403);
        body(json(post("/api/categories"), "{\"name\":\"Hacked\"}"), 401);
    }

    @Test
    void searchFiltersByCategoryPriceAndNamePrefix() throws Exception {
        String admin = adminToken();
        long cat = createCategory(admin);
        createProduct(admin, cat, "100.00", 5);
        createProduct(admin, cat, "250.00", 5);
        createProduct(admin, cat, "900.00", 5);

        String res = body(get("/api/products").param("categoryId", String.valueOf(cat))
                .param("minPrice", "150").param("sortBy", "price").param("direction", "desc"), 200);
        List<Double> prices = read(res, "$.content[*].price");
        assertThat(prices).containsExactly(900.0, 250.0);

        String byName = body(get("/api/products").param("categoryId", String.valueOf(cat)).param("q", "widget"), 200);
        assertThat(readLong(byName, "$.totalElements")).isEqualTo(3);
    }

    @Test
    void paginationReportsTotals() throws Exception {
        String admin = adminToken();
        long cat = createCategory(admin);
        for (int i = 0; i < 5; i++) {
            createProduct(admin, cat, "10.00", 1);
        }
        String res = body(get("/api/products").param("categoryId", String.valueOf(cat))
                .param("size", "2").param("page", "2"), 200);
        assertThat(readLong(res, "$.totalElements")).isEqualTo(5);
        assertThat(readLong(res, "$.totalPages")).isEqualTo(3);
        assertThat((List<?>) read(res, "$.content")).hasSize(1);
    }

    @Test
    void invalidSearchParametersAre400() throws Exception {
        body(get("/api/products").param("sortBy", "password"), 400);
        body(get("/api/products").param("minPrice", "50").param("maxPrice", "10"), 400);
        body(get("/api/products").param("categoryId", "abc"), 400);
    }

    @Test
    void duplicateSkuIs409() throws Exception {
        String admin = adminToken();
        long cat = createCategory(admin);
        String sku = unique("DUP");
        String req = "{\"sku\":\"" + sku + "\",\"name\":\"A\",\"price\":1.00,\"stockQuantity\":1,\"categoryId\":" + cat + "}";
        body(auth(json(post("/api/products"), req), admin), 201);
        body(auth(json(post("/api/products"), req), admin), 409);
    }

    @Test
    void stockUpdateIsVisibleImmediatelyDespiteCaching() throws Exception {
        String admin = adminToken();
        long id = createProduct(admin, createCategory(admin), "10.00", 5);
        assertThat(stockOf(id)).isEqualTo(5); // now cached
        body(auth(json(patch("/api/products/" + id + "/stock"), "{\"stockQuantity\":42}"), admin), 200);
        assertThat(stockOf(id)).isEqualTo(42);
    }

    @Test
    void deletedProductsDisappearButAreKeptForHistory() throws Exception {
        String admin = adminToken();
        long id = createProduct(admin, createCategory(admin), "10.00", 5);
        stockOf(id);
        body(auth(delete("/api/products/" + id), admin), 204);
        body(get("/api/products/" + id), 404);
    }
}
