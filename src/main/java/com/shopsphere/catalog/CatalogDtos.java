package com.shopsphere.catalog;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description) {
    }

    // Cached responses implement Serializable so they can be stored in Redis.
    public record CategoryResponse(Long id, String name, String description) implements Serializable {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
        }
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,40}$", message = "must be 3-40 upper-case letters, digits or dashes") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            @Size(max = 500) String imageUrl,
            @NotNull Long categoryId) {
    }

    public record StockUpdateRequest(@NotNull @Min(0) Integer stockQuantity) {
    }

    public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price,
                                  int stockQuantity, boolean inStock, String imageUrl, Long categoryId,
                                  String categoryName) implements Serializable {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getStockQuantity(), p.getStockQuantity() > 0, p.getImageUrl(),
                    p.getCategory().getId(), p.getCategory().getName());
        }
    }
}
