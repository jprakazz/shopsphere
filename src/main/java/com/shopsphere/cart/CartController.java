package com.shopsphere.cart;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.cart.CartDtos.AddItemRequest;
import com.shopsphere.cart.CartDtos.CartResponse;
import com.shopsphere.cart.CartDtos.UpdateQuantityRequest;
import com.shopsphere.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse view(@AuthenticationPrincipal AuthUser user) {
        return cartService.view(user.id());
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AddItemRequest req) {
        return cartService.addItem(user.id(), req);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId,
                                       @Valid @RequestBody UpdateQuantityRequest req) {
        return cartService.updateQuantity(user.id(), productId, req.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId) {
        return cartService.removeItem(user.id(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthUser user) {
        cartService.clear(user.id());
    }
}
