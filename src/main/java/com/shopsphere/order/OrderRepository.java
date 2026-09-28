package com.shopsphere.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    /**
     * SELECT ... FOR UPDATE. Every state change goes through this lock, so a payment and a cancel
     * racing on the same order are serialised instead of both succeeding.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Served by idx_orders_user_created. Items are not fetched: the history list shows totals only. */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("select o.id from Order o where o.status = :status and o.createdAt < :cutoff order by o.id")
    List<Long> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status,
                                            @Param("cutoff") LocalDateTime cutoff, Limit limit);
}
