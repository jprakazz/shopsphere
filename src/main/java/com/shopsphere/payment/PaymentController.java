package com.shopsphere.payment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.payment.PaymentDtos.PaymentRequest;
import com.shopsphere.payment.PaymentDtos.PaymentResponse;
import com.shopsphere.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 201 when the charge succeeds, 402 Payment Required when the gateway declines it. */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId,
                                               @Valid @RequestBody PaymentRequest req) {
        PaymentResponse payment = paymentService.pay(user.id(), orderId, req);
        HttpStatus status = payment.status() == PaymentStatus.SUCCEEDED ? HttpStatus.CREATED : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(payment);
    }

    @GetMapping
    public List<PaymentResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId) {
        return paymentService.list(user.id(), orderId);
    }
}
