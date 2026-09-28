package com.shopsphere.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.shopsphere.order.OrderEvents.OrderEvent;

/**
 * Used when Kafka is disabled (local development): delivers order events straight to the
 * notification service, asynchronously and only after the order transaction has committed.
 */
@Component
@EnableAsync
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class InProcessOrderEventRelay {

    private final NotificationService notificationService;

    public InProcessOrderEventRelay(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener
    public void on(OrderEvent event) {
        notificationService.notify(event);
    }
}
