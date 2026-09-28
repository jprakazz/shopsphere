package com.shopsphere.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(@NotNull Long addressId) {
    }

    public record StatusUpdateRequest(@NotNull OrderStatus status) {
    }

    public record OrderItemResponse(Long productId, String productName, BigDecimal unitPrice, int quantity,
                                    BigDecimal lineTotal) {
        static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(),
                    i.lineTotal());
        }
    }

    public record OrderResponse(Long id, String orderNumber, OrderStatus status, BigDecimal totalAmount,
                                String shippingAddress, List<OrderItemResponse> items, LocalDateTime createdAt) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getTotalAmount(),
                    o.getShippingAddress(), o.getItems().stream().map(OrderItemResponse::from).toList(),
                    o.getCreatedAt());
        }
    }

    public record OrderSummary(Long id, String orderNumber, OrderStatus status, BigDecimal totalAmount,
                               LocalDateTime createdAt) {
        public static OrderSummary from(Order o) {
            return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus(), o.getTotalAmount(),
                    o.getCreatedAt());
        }
    }

    /** Checkout result plus whether it was a replay of an earlier request with the same idempotency key. */
    public record CheckoutResult(OrderResponse order, boolean replayed) {
    }
}
