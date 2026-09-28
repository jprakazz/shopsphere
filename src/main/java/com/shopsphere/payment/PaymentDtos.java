package com.shopsphere.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** paymentToken is the provider's tokenised card/UPI handle; raw card numbers never reach this API. */
    public record PaymentRequest(@NotNull PaymentMethod method, @NotBlank @Size(max = 100) String paymentToken) {
    }

    public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentMethod method,
                                  PaymentStatus status, String gatewayReference, String failureReason,
                                  LocalDateTime createdAt) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getMethod(), p.getStatus(),
                    p.getGatewayReference(), p.getFailureReason(), p.getCreatedAt());
        }
    }
}
