package com.shopsphere.notification;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shopsphere.order.OrderEvents.OrderEvent;
import com.shopsphere.order.OrderStatus;
import com.shopsphere.user.User;
import com.shopsphere.user.UserRepository;

/**
 * Turns order events into customer notifications. Delivery is simulated with a log line;
 * a real deployment would call an email/SMS provider (e.g. Amazon SES / SNS) here.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository users;
    private final AtomicLong sent = new AtomicLong();

    public NotificationService(UserRepository users) {
        this.users = users;
    }

    public void notify(OrderEvent event) {
        String email = users.findById(event.userId()).map(User::getEmail).orElse("unknown");
        String message = switch (event.type()) {
            case ORDER_PLACED -> "Thanks! Order " + event.orderNumber() + " is placed. Amount due: " + event.amount();
            case ORDER_PAID -> "Payment received for order " + event.orderNumber();
            case ORDER_SHIPPED -> "Order " + event.orderNumber() + " has shipped";
            case ORDER_DELIVERED -> "Order " + event.orderNumber() + " was delivered";
            case ORDER_CANCELLED -> "Order " + event.orderNumber() + " was cancelled"
                    + (event.previousStatus() == OrderStatus.PAID ? " and refunded" : "");
        };
        sent.incrementAndGet();
        log.info("[notification] to={} message=\"{}\"", email, message);
    }

    public long sentCount() {
        return sent.get();
    }
}
