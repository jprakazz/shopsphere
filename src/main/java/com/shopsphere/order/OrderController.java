package com.shopsphere.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.PageResponse;
import com.shopsphere.order.OrderDtos.CheckoutRequest;
import com.shopsphere.order.OrderDtos.OrderResponse;
import com.shopsphere.order.OrderDtos.OrderSummary;
import com.shopsphere.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order from the caller's cart. Clients should send a unique Idempotency-Key per checkout
     * attempt; retrying with the same key returns the original order (200) instead of creating another (201).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal AuthUser user,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody CheckoutRequest req) {
        var result = orderService.checkout(user.id(), req, key);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.order());
    }

    @GetMapping
    public PageResponse<OrderSummary> history(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return orderService.history(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.get(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.cancel(user.id(), id);
    }
}
