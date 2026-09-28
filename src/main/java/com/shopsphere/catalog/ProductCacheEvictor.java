package com.shopsphere.catalog;

import java.util.Collection;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts cached product details once the surrounding transaction commits.
 * Evicting earlier would let a concurrent reader re-cache the old stock value before the commit.
 */
@Component
public class ProductCacheEvictor {

    private final CacheManager cacheManager;

    public ProductCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAfterCommit(Collection<Long> productIds) {
        List<Long> ids = List.copyOf(productIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(ids);
                }
            });
        } else {
            evictNow(ids);
        }
    }

    private void evictNow(List<Long> ids) {
        Cache cache = cacheManager.getCache(ProductService.PRODUCT_CACHE);
        if (cache != null) {
            ids.forEach(cache::evict);
        }
    }
}
