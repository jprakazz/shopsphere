package com.shopsphere.catalog;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the catalogue search WHERE clause from only the filters the caller supplied.
 * Avoiding "(:param IS NULL OR ...)" keeps the generated SQL index-friendly.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> search(Long categoryId, String nameQuery, BigDecimal minPrice, BigDecimal maxPrice) {
        Specification<Product> spec = (root, q, cb) -> cb.isTrue(root.get("active"));
        if (categoryId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (nameQuery != null && !nameQuery.isBlank()) {
            // Prefix match so the idx_products_name index can be used (a leading % would force a full scan).
            String pattern = escapeLike(nameQuery.trim()) + "%";
            spec = spec.and((root, q, cb) -> cb.like(root.get("name"), pattern, '\\'));
        }
        if (minPrice != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        return spec;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
