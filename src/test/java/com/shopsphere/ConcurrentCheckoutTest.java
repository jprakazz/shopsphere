package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shopsphere.cart.CartDtos.AddItemRequest;
import com.shopsphere.cart.CartService;
import com.shopsphere.catalog.CatalogDtos.CategoryRequest;
import com.shopsphere.catalog.CatalogDtos.ProductRequest;
import com.shopsphere.catalog.CategoryService;
import com.shopsphere.catalog.ProductRepository;
import com.shopsphere.catalog.ProductService;
import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.order.OrderDtos.CheckoutRequest;
import com.shopsphere.order.OrderService;
import com.shopsphere.user.AddressService;
import com.shopsphere.user.AuthService;
import com.shopsphere.user.UserDtos.AddressRequest;
import com.shopsphere.user.UserDtos.RegisterRequest;

/** Many buyers race for the last few units: exactly as many orders succeed as there is stock. */
@SpringBootTest(properties = "app.orders.expiry-job-enabled=false")
class ConcurrentCheckoutTest {

    private static final int BUYERS = 30;
    private static final int STOCK = 7;

    @Autowired AuthService authService;
    @Autowired AddressService addressService;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired CategoryService categoryService;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;

    @Test
    void neverOversellsUnderConcurrentCheckout() throws Exception {
        long categoryId = categoryService.create(new CategoryRequest(ApiTestSupport.unique("Race"), null)).id();
        long productId = productService.create(new ProductRequest(ApiTestSupport.unique("RACE"), "Limited Edition",
                null, new BigDecimal("999.00"), STOCK, null, categoryId)).id();

        record Buyer(long userId, long addressId) {
        }
        List<Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            String email = ApiTestSupport.unique("racer").toLowerCase() + "@example.com";
            long userId = authService.register(new RegisterRequest("Racer " + i, email, "Secret@123", null)).user().id();
            long addressId = addressService.add(userId,
                    new AddressRequest("1 Main St", null, "Pune", "MH", "411001", "India", true)).id();
            cartService.addItem(userId, new AddItemRequest(productId, 1));
            buyers.add(new Buyer(userId, addressId));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(BUYERS)) {
            for (Buyer b : buyers) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        orderService.checkout(b.userId(), new CheckoutRequest(b.addressId()), null);
                        return true;
                    } catch (BusinessRuleException soldOut) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(STOCK);
        }
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isZero();
    }
}
