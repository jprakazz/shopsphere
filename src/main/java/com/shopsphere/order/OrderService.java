package com.shopsphere.order;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.cart.Cart;
import com.shopsphere.cart.CartRepository;
import com.shopsphere.catalog.ProductCacheEvictor;
import com.shopsphere.catalog.ProductRepository;
import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.common.NotFoundException;
import com.shopsphere.common.PageResponse;
import com.shopsphere.order.OrderDtos.CheckoutRequest;
import com.shopsphere.order.OrderDtos.CheckoutResult;
import com.shopsphere.order.OrderDtos.OrderResponse;
import com.shopsphere.order.OrderDtos.OrderSummary;
import com.shopsphere.order.OrderEvents.OrderEvent;
import com.shopsphere.user.AddressService;

@Service
public class OrderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final CartRepository carts;
    private final ProductRepository products;
    private final AddressService addressService;
    private final ProductCacheEvictor cacheEvictor;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orders, CartRepository carts, ProductRepository products,
                        AddressService addressService, ProductCacheEvictor cacheEvictor,
                        ApplicationEventPublisher events) {
        this.orders = orders;
        this.carts = carts;
        this.products = products;
        this.addressService = addressService;
        this.cacheEvictor = cacheEvictor;
        this.events = events;
    }

    /** What checkout needs from a cart line: a snapshot of name and price at the moment of purchase. */
    private record Line(Long productId, String name, BigDecimal price, int quantity) {
    }

    /**
     * Converts the caller's cart into an order in one transaction:
     * <ol>
     *   <li>replay the earlier result if this idempotency key was already used,</li>
     *   <li>reserve stock for every line with an atomic conditional UPDATE,</li>
     *   <li>save the order with price/name snapshots and empty the cart,</li>
     *   <li>after commit, evict cached products and publish ORDER_PLACED.</li>
     * </ol>
     * If any line cannot be reserved the exception rolls back every earlier reservation.
     */
    @Transactional
    public CheckoutResult checkout(Long userId, CheckoutRequest req, String idempotencyKey) {
        String key = normaliseKey(idempotencyKey);
        if (key != null) {
            var previous = orders.findByUserIdAndIdempotencyKey(userId, key);
            if (previous.isPresent()) {
                return new CheckoutResult(OrderResponse.from(previous.get()), true);
            }
        }

        Cart cart = carts.findByUserId(userId).filter(c -> !c.isEmpty())
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));
        String shippingAddress = addressService.getOwned(userId, req.addressId()).toSnapshot();

        // Reserve in product-id order so concurrent checkouts lock rows in the same order (no deadlocks).
        List<Line> lines = cart.getItems().stream()
                .map(i -> new Line(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getPrice(),
                        i.getQuantity()))
                .sorted(Comparator.comparing(Line::productId))
                .toList();

        Order order = new Order(newOrderNumber(), userId, shippingAddress, key);
        for (Line line : lines) {
            if (products.reserveStock(line.productId(), line.quantity()) == 0) {
                throw new BusinessRuleException("Insufficient stock for " + line.name());
            }
            order.addItem(line.productId(), line.name(), line.price(), line.quantity());
        }
        orders.save(order);
        cart.clear();

        cacheEvictor.evictAfterCommit(lines.stream().map(Line::productId).toList());
        events.publishEvent(OrderEvent.of(OrderEvents.Type.ORDER_PLACED, order, null));
        return new CheckoutResult(OrderResponse.from(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        return OrderResponse.from(loadOwned(userId, orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> history(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(orders.findByUserId(userId, pageable), OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> listForAdmin(OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return PageResponse.of(result, OrderSummary::from);
    }

    /** Customer-initiated cancel; allowed until the order ships. */
    @Transactional
    public OrderResponse cancel(Long userId, Long orderId) {
        return OrderResponse.from(transition(lockOwned(userId, orderId), OrderStatus.CANCELLED));
    }

    /** Admin fulfilment updates (SHIPPED, DELIVERED) and admin cancels. */
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus next) {
        if (next == OrderStatus.PAID) {
            throw new BusinessRuleException("Orders become PAID only through a successful payment");
        }
        Order order = orders.lockById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        return OrderResponse.from(transition(order, next));
    }

    /** Called by the payment module after the gateway confirms the charge. */
    @Transactional
    public Order markPaid(Order order) {
        return transition(order, OrderStatus.PAID);
    }

    /**
     * Cancels orders whose payment never arrived so their reserved stock returns to the shelf.
     * Each order is locked and re-checked, so a payment completing at the same moment wins cleanly.
     */
    @Transactional
    public int expireUnpaid(Duration timeout) {
        var ids = orders.findIdsByStatusCreatedBefore(OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().minus(timeout), Limit.of(100));
        int expired = 0;
        for (Long id : ids) {
            var order = orders.lockById(id).filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT);
            if (order.isPresent()) {
                transition(order.get(), OrderStatus.CANCELLED);
                expired++;
            }
        }
        return expired;
    }

    @Transactional(readOnly = true)
    public Order loadOwned(Long userId, Long orderId) {
        Order order = load(orderId);
        if (!order.getUserId().equals(userId)) {
            // Report "not found" rather than "forbidden" so order ids cannot be probed.
            throw new NotFoundException("Order", orderId);
        }
        return order;
    }

    /** Like loadOwned, but takes a row lock; use before changing the order's status. */
    @Transactional
    public Order lockOwned(Long userId, Long orderId) {
        Order order = orders.lockById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!order.getUserId().equals(userId)) {
            throw new NotFoundException("Order", orderId);
        }
        return order;
    }

    private Order load(Long orderId) {
        return orders.findWithItemsById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private Order transition(Order order, OrderStatus next) {
        OrderStatus previous = order.getStatus();
        order.moveTo(next);
        if (next == OrderStatus.CANCELLED) {
            order.getItems().forEach(i -> products.releaseStock(i.getProductId(), i.getQuantity()));
            cacheEvictor.evictAfterCommit(order.getItems().stream().map(OrderItem::getProductId).toList());
        }
        events.publishEvent(OrderEvent.of(OrderEvent.forStatus(next), order, previous));
        return order;
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.length() > 80) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 80 characters");
        }
        return key.trim();
    }

    private static String newOrderNumber() {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return "SS-" + LocalDate.now().format(DAY) + "-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
