package com.shopsphere.payment;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.order.Order;
import com.shopsphere.order.OrderEvents;
import com.shopsphere.order.OrderEvents.OrderEvent;
import com.shopsphere.order.OrderService;
import com.shopsphere.order.OrderStatus;
import com.shopsphere.payment.PaymentDtos.PaymentRequest;
import com.shopsphere.payment.PaymentDtos.PaymentResponse;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OrderService orderService;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OrderService orderService) {
        this.payments = payments;
        this.gateway = gateway;
        this.orderService = orderService;
    }

    /**
     * Charges the order total. A declined charge is recorded and returned (not thrown) so the
     * attempt is persisted and the customer can retry with another method.
     */
    @Transactional
    public PaymentResponse pay(Long userId, Long orderId, PaymentRequest req) {
        Order order = orderService.lockOwned(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and cannot be paid");
        }
        var result = gateway.charge(order.getOrderNumber(), order.getTotalAmount(), req.method(), req.paymentToken());
        Payment payment = payments.save(new Payment(order.getId(), order.getTotalAmount(), req.method(),
                result.success() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                result.reference(), result.failureReason()));
        if (result.success()) {
            orderService.markPaid(order);
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(Long userId, Long orderId) {
        orderService.loadOwned(userId, orderId);
        return payments.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentResponse::from).toList();
    }

    /** Runs inside the cancelling transaction: a paid order that is cancelled is refunded atomically. */
    @EventListener
    public void onOrderEvent(OrderEvent event) {
        if (event.type() == OrderEvents.Type.ORDER_CANCELLED && event.previousStatus() == OrderStatus.PAID) {
            payments.findFirstByOrderIdAndStatus(event.orderId(), PaymentStatus.SUCCEEDED).ifPresent(p -> {
                gateway.refund(p.getGatewayReference(), p.getAmount());
                p.markRefunded();
            });
        }
    }
}
