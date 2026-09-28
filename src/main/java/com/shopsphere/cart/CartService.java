package com.shopsphere.cart;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.cart.CartDtos.AddItemRequest;
import com.shopsphere.cart.CartDtos.CartResponse;
import com.shopsphere.catalog.Product;
import com.shopsphere.catalog.ProductRepository;
import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.common.NotFoundException;

@Service
public class CartService {

    private final CartRepository carts;
    private final ProductRepository products;

    public CartService(CartRepository carts, ProductRepository products) {
        this.carts = carts;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public CartResponse view(Long userId) {
        return carts.findByUserId(userId).map(CartResponse::from).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddItemRequest req) {
        Product product = products.findById(req.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product", req.productId()));
        Cart cart = carts.findByUserId(userId).orElseGet(() -> carts.save(new Cart(userId)));
        int newQuantity = cart.find(product.getId()).map(CartItem::getQuantity).orElse(0) + req.quantity();
        checkQuantity(product, newQuantity);
        cart.add(product, req.quantity());
        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long productId, int quantity) {
        Cart cart = carts.findByUserId(userId).orElseThrow(() -> new NotFoundException("Cart item", productId));
        CartItem item = cart.find(productId).orElseThrow(() -> new NotFoundException("Cart item", productId));
        if (quantity == 0) {
            cart.remove(productId);
        } else {
            checkQuantity(item.getProduct(), quantity);
            item.setQuantity(quantity);
        }
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        return updateQuantity(userId, productId, 0);
    }

    @Transactional
    public void clear(Long userId) {
        carts.findByUserId(userId).ifPresent(Cart::clear);
    }

    /**
     * Early, friendly check only. Stock can still change before checkout; the authoritative check
     * is the atomic reservation in OrderService.
     */
    private static void checkQuantity(Product product, int quantity) {
        if (quantity > CartDtos.MAX_QUANTITY_PER_ITEM) {
            throw new BusinessRuleException("At most " + CartDtos.MAX_QUANTITY_PER_ITEM + " units per product");
        }
        if (quantity > product.getStockQuantity()) {
            throw new BusinessRuleException("Only " + product.getStockQuantity() + " units of "
                    + product.getName() + " are in stock");
        }
    }
}
