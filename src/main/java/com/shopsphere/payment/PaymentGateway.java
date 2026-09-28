package com.shopsphere.payment;

import java.math.BigDecimal;

/**
 * Boundary to an external payment provider (Razorpay, Stripe, ...). The rest of the system only
 * depends on this interface, so swapping providers does not touch order or payment logic.
 */
public interface PaymentGateway {

    ChargeResult charge(String orderNumber, BigDecimal amount, PaymentMethod method, String paymentToken);

    void refund(String gatewayReference, BigDecimal amount);

    record ChargeResult(boolean success, String reference, String failureReason) {

        public static ChargeResult ok(String reference) {
            return new ChargeResult(true, reference, null);
        }

        public static ChargeResult declined(String reason) {
            return new ChargeResult(false, null, reason);
        }
    }
}
