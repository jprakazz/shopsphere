package com.shopsphere.order;

import java.math.BigDecimal;
import java.time.Instant;

/** Domain events published by the order module. Listeners run after the transaction commits. */
public final class OrderEvents {

    private OrderEvents() {
    }

    public enum Type {
        ORDER_PLACED,
        ORDER_PAID,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        ORDER_CANCELLED
    }

    public record OrderEvent(Type type, Long orderId, String orderNumber, Long userId, BigDecimal amount,
                             OrderStatus previousStatus, Instant occurredAt) {

        public static OrderEvent of(Type type, Order order, OrderStatus previousStatus) {
            return new OrderEvent(type, order.getId(), order.getOrderNumber(), order.getUserId(),
                    order.getTotalAmount(), previousStatus, Instant.now());
        }

        public static Type forStatus(OrderStatus status) {
            return switch (status) {
                case PENDING_PAYMENT -> Type.ORDER_PLACED;
                case PAID -> Type.ORDER_PAID;
                case SHIPPED -> Type.ORDER_SHIPPED;
                case DELIVERED -> Type.ORDER_DELIVERED;
                case CANCELLED -> Type.ORDER_CANCELLED;
            };
        }
    }
}
