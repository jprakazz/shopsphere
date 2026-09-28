package com.shopsphere.cart;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartDtos {

    public static final int MAX_QUANTITY_PER_ITEM = 20;

    private CartDtos() {
    }

    public record AddItemRequest(@NotNull Long productId,
                                 @NotNull @Min(1) @Max(MAX_QUANTITY_PER_ITEM) Integer quantity) {
    }

    public record UpdateQuantityRequest(@NotNull @Min(0) @Max(MAX_QUANTITY_PER_ITEM) Integer quantity) {
    }

    public record CartItemResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                                   BigDecimal lineTotal, boolean available) {
        static CartItemResponse from(CartItem i) {
            var p = i.getProduct();
            boolean available = p.isActive() && p.getStockQuantity() >= i.getQuantity();
            return new CartItemResponse(p.getId(), p.getSku(), p.getName(), p.getPrice(), i.getQuantity(),
                    i.lineTotal(), available);
        }
    }

    public record CartResponse(List<CartItemResponse> items, int totalItems, BigDecimal subtotal) {
        static CartResponse from(Cart cart) {
            var items = cart.getItems().stream().map(CartItemResponse::from).toList();
            int count = items.stream().mapToInt(CartItemResponse::quantity).sum();
            BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CartResponse(items, count, subtotal);
        }

        static CartResponse empty() {
            return new CartResponse(List.of(), 0, BigDecimal.ZERO);
        }
    }
}
