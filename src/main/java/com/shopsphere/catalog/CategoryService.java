package com.shopsphere.catalog;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.catalog.CatalogDtos.CategoryRequest;
import com.shopsphere.catalog.CatalogDtos.CategoryResponse;
import com.shopsphere.common.BusinessRuleException;
import com.shopsphere.common.NotFoundException;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categories.existsByNameIgnoreCase(req.name())) {
            throw new BusinessRuleException("Category '" + req.name() + "' already exists");
        }
        return CategoryResponse.from(categories.save(new Category(req.name().trim(), req.description())));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category category = get(id);
        category.rename(req.name().trim(), req.description());
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }
}
