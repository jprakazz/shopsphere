package com.shopsphere.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sandbox gateway. Any token succeeds except those starting with "tok_fail", which are declined,
 * mirroring how real providers expose test tokens for failure scenarios.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public ChargeResult charge(String orderNumber, BigDecimal amount, PaymentMethod method, String paymentToken) {
        if (paymentToken != null && paymentToken.startsWith("tok_fail")) {
            log.info("Mock gateway declined {} for order {}", amount, orderNumber);
            return ChargeResult.declined("Payment declined by issuer");
        }
        String reference = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("Mock gateway charged {} via {} for order {} ({})", amount, method, orderNumber, reference);
        return ChargeResult.ok(reference);
    }

    @Override
    public void refund(String gatewayReference, BigDecimal amount) {
        log.info("Mock gateway refunded {} for {}", amount, gatewayReference);
    }
}
