package com.shopsphere.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.shopsphere.common.BusinessRuleException;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, PAID, true",
            "PENDING_PAYMENT, CANCELLED, true",
            "PENDING_PAYMENT, SHIPPED, false",
            "PAID, SHIPPED, true",
            "PAID, CANCELLED, true",
            "SHIPPED, DELIVERED, true",
            "SHIPPED, CANCELLED, false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PAID, false"
    })
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void orderTotalsItsLinesAndEnforcesTheStateMachine() {
        Order order = new Order("SS-TEST", 1L, "addr", null);
        order.addItem(1L, "A", new BigDecimal("10.50"), 2);
        order.addItem(2L, "B", new BigDecimal("3.00"), 1);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("24.00");

        assertThatThrownBy(() -> order.moveTo(OrderStatus.DELIVERED)).isInstanceOf(BusinessRuleException.class);
        order.moveTo(OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
