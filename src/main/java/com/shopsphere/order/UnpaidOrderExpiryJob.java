package com.shopsphere.order;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases stock held by orders that were never paid. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.orders.expiry-job-enabled", havingValue = "true", matchIfMissing = true)
public class UnpaidOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryJob.class);

    private final OrderService orderService;
    private final Duration timeout;

    public UnpaidOrderExpiryJob(OrderService orderService,
                                @Value("${app.orders.payment-timeout:30m}") Duration timeout) {
        this.orderService = orderService;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval:1m}")
    public void run() {
        int expired = orderService.expireUnpaid(timeout);
        if (expired > 0) {
            log.info("Cancelled {} orders unpaid for more than {}", expired, timeout);
        }
    }
}
