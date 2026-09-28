package com.shopsphere.catalog;

import java.math.BigDecimal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.catalog.CatalogDtos.ProductRequest;
import com.shopsphere.catalog.CatalogDtos.ProductResponse;
import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.common.NotFoundException;
import com.shopsphere.common.PageResponse;

@Service
public class ProductService {

    public static final String PRODUCT_CACHE = "product";

    private final ProductRepository products;
    private final CategoryService categoryService;

    public ProductService(ProductRepository products, CategoryService categoryService) {
        this.products = products;
        this.categoryService = categoryService;
    }

    /**
     * Product detail is the hottest read in the system, so it is served from cache.
     * Any write to a product evicts its entry; see also OrderService, which evicts after stock changes.
     */
    @Cacheable(value = PRODUCT_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return products.findWithCategoryById(id)
                .filter(Product::isActive)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(Long categoryId, String q, BigDecimal minPrice, BigDecimal maxPrice,
                                                Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");
        }
        var spec = ProductSpecifications.search(categoryId, q, minPrice, maxPrice);
        return PageResponse.of(products.findAll(spec, pageable), ProductResponse::from);
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        if (products.existsBySku(req.sku())) {
            throw new BusinessRuleException("SKU " + req.sku() + " already exists");
        }
        Category category = categoryService.get(req.categoryId());
        Product saved = products.save(new Product(req.sku(), req.name().trim(), req.description(), req.price(),
                req.stockQuantity(), req.imageUrl(), category));
        return ProductResponse.from(saved);
    }

    @CacheEvict(value = PRODUCT_CACHE, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product product = load(id);
        if (!product.getSku().equals(req.sku())) {
            throw new IllegalArgumentException("SKU cannot be changed");
        }
        product.update(req.name().trim(), req.description(), req.price(), req.imageUrl(),
                categoryService.get(req.categoryId()));
        product.setStockQuantity(req.stockQuantity());
        return ProductResponse.from(product);
    }

    @CacheEvict(value = PRODUCT_CACHE, key = "#id")
    @Transactional
    public ProductResponse updateStock(Long id, int stockQuantity) {
        Product product = load(id);
        product.setStockQuantity(stockQuantity);
        return ProductResponse.from(product);
    }

    /** Soft delete: past orders still reference the product, so the row is kept. */
    @CacheEvict(value = PRODUCT_CACHE, key = "#id")
    @Transactional
    public void deactivate(Long id) {
        load(id).deactivate();
    }

    private Product load(Long id) {
        return products.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
