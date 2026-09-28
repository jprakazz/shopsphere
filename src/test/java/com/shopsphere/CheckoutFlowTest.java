package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopsphere.order.OrderService;

class CheckoutFlowTest extends ApiTestSupport {

    @Autowired
    OrderService orderService;

    String admin;
    String customer;
    long address;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        customer = registerCustomer();
        address = createAddress(customer);
        category = createCategory(admin);
    }

    @Test
    void happyPathFromCartToDelivery() throws Exception {
        long phone = createProduct(admin, category, "24999.00", 10);
        long book = createProduct(admin, category, "1899.50", 10);
        addToCart(customer, phone, 2);
        addToCart(customer, book, 1);

        String order = body(auth(json(post("/api/orders"), "{\"addressId\":" + address + "}"), customer), 201);
        long orderId = readLong(order, "$.id");
        assertThat(read(order, "$.status").toString()).isEqualTo("PENDING_PAYMENT");
        assertThat(((Number) read(order, "$.totalAmount")).doubleValue()).isEqualTo(51897.50);
        assertThat(read(order, "$.shippingAddress").toString()).contains("Bengaluru");

        assertThat(stockOf(phone)).isEqualTo(8);
        assertThat(stockOf(book)).isEqualTo(9);
        assertThat(readLong(body(auth(get("/api/cart"), customer), 200), "$.totalItems")).isZero();

        body(auth(json(post("/api/orders/" + orderId + "/payments"),
                "{\"method\":\"UPI\",\"paymentToken\":\"tok_ok\"}"), customer), 201);
        body(auth(json(patch("/api/admin/orders/" + orderId + "/status"), "{\"status\":\"SHIPPED\"}"), admin), 200);
        String delivered = body(auth(json(patch("/api/admin/orders/" + orderId + "/status"),
                "{\"status\":\"DELIVERED\"}"), admin), 200);
        assertThat(read(delivered, "$.status").toString()).isEqualTo("DELIVERED");

        String history = body(auth(get("/api/orders"), customer), 200);
        assertThat(readLong(history, "$.totalElements")).isEqualTo(1);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsTheOriginalOrder() throws Exception {
        long product = createProduct(admin, category, "10.00", 10);
        addToCart(customer, product, 3);
        var req = json(post("/api/orders"), "{\"addressId\":" + address + "}").header("Idempotency-Key", "abc-123");

        String first = body(auth(req, customer), 201);
        String second = body(auth(json(post("/api/orders"), "{\"addressId\":" + address + "}")
                .header("Idempotency-Key", "abc-123"), customer), 200);

        assertThat(readLong(second, "$.id")).isEqualTo(readLong(first, "$.id"));
        assertThat(stockOf(product)).isEqualTo(7); // reserved once, not twice
    }

    @Test
    void insufficientStockRollsBackEveryLine() throws Exception {
        long plenty = createProduct(admin, category, "10.00", 10);
        long scarce = createProduct(admin, category, "10.00", 2);
        addToCart(customer, plenty, 5);
        addToCart(customer, scarce, 2);
        // Someone else buys the scarce item after it went into our cart.
        body(auth(json(patch("/api/products/" + scarce + "/stock"), "{\"stockQuantity\":1}"), admin), 200);

        body(auth(json(post("/api/orders"), "{\"addressId\":" + address + "}"), customer), 409);

        assertThat(stockOf(plenty)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        assertThat(readLong(body(auth(get("/api/cart"), customer), 200), "$.totalItems")).isEqualTo(7);
    }

    @Test
    void declinedPaymentIsRecordedAndCanBeRetried() throws Exception {
        long product = createProduct(admin, category, "99.00", 5);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, address);

        body(auth(json(post("/api/orders/" + orderId + "/payments"),
                "{\"method\":\"CARD\",\"paymentToken\":\"tok_fail_card\"}"), customer), 402);
        assertThat(read(body(auth(get("/api/orders/" + orderId), customer), 200), "$.status").toString())
                .isEqualTo("PENDING_PAYMENT");

        body(auth(json(post("/api/orders/" + orderId + "/payments"),
                "{\"method\":\"CARD\",\"paymentToken\":\"tok_visa\"}"), customer), 201);
        String payments = body(auth(get("/api/orders/" + orderId + "/payments"), customer), 200);
        assertThat(read(payments, "$[*].status").toString()).isEqualTo("[\"FAILED\",\"SUCCEEDED\"]");

        // A paid order cannot be charged twice.
        body(auth(json(post("/api/orders/" + orderId + "/payments"),
                "{\"method\":\"CARD\",\"paymentToken\":\"tok_visa\"}"), customer), 409);
    }

    @Test
    void cancellingAPaidOrderRestocksAndRefunds() throws Exception {
        long product = createProduct(admin, category, "50.00", 5);
        addToCart(customer, product, 2);
        long orderId = checkout(customer, address);
        body(auth(json(post("/api/orders/" + orderId + "/payments"),
                "{\"method\":\"UPI\",\"paymentToken\":\"tok_ok\"}"), customer), 201);

        String cancelled = body(auth(post("/api/orders/" + orderId + "/cancel"), customer), 200);
        assertThat(read(cancelled, "$.status").toString()).isEqualTo("CANCELLED");
        assertThat(stockOf(product)).isEqualTo(5);
        String payments = body(auth(get("/api/orders/" + orderId + "/payments"), customer), 200);
        assertThat(read(payments, "$[0].status").toString()).isEqualTo("REFUNDED");
    }

    @Test
    void illegalTransitionsAreRejected() throws Exception {
        long product = createProduct(admin, category, "50.00", 5);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, address);

        // Cannot ship an unpaid order, and admins cannot mark an order paid by hand.
        body(auth(json(patch("/api/admin/orders/" + orderId + "/status"), "{\"status\":\"SHIPPED\"}"), admin), 409);
        body(auth(json(patch("/api/admin/orders/" + orderId + "/status"), "{\"status\":\"PAID\"}"), admin), 409);
        // Customers cannot use admin endpoints.
        body(auth(json(patch("/api/admin/orders/" + orderId + "/status"), "{\"status\":\"CANCELLED\"}"), customer), 403);
    }

    @Test
    void customersCannotSeeEachOthersOrders() throws Exception {
        long product = createProduct(admin, category, "50.00", 5);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, address);

        String stranger = registerCustomer();
        body(auth(get("/api/orders/" + orderId), stranger), 404);
        body(auth(post("/api/orders/" + orderId + "/cancel"), stranger), 404);
        // Nor ship to someone else's address.
        addToCart(stranger, product, 1);
        body(auth(json(post("/api/orders"), "{\"addressId\":" + address + "}"), stranger), 404);
    }

    @Test
    void unpaidOrdersExpireAndReleaseStock() throws Exception {
        long product = createProduct(admin, category, "50.00", 5);
        addToCart(customer, product, 3);
        long orderId = checkout(customer, address);
        assertThat(stockOf(product)).isEqualTo(2);

        orderService.expireUnpaid(Duration.ZERO);

        assertThat(read(body(auth(get("/api/orders/" + orderId), customer), 200), "$.status").toString())
                .isEqualTo("CANCELLED");
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        body(auth(json(post("/api/orders"), "{\"addressId\":" + address + "}"), customer), 409);
    }
}
