package com.shopsphere.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
