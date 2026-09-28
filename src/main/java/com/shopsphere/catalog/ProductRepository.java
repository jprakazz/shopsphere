package com.shopsphere.catalog;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Loads the category in the same query to avoid an N+1 when building responses. */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsBySku(String sku);

    /**
     * Reserves stock in a single atomic statement. The WHERE clause only matches while enough stock
     * remains, so two concurrent checkouts can never both take the last unit: the loser gets 0 rows.
     * The version bump keeps admin edits (which use optimistic locking) consistent with this update.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p
               set p.stockQuantity = p.stockQuantity - :qty, p.version = p.version + 1
             where p.id = :id and p.active = true and p.stockQuantity >= :qty
            """)
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :qty, p.version = p.version + 1 where p.id = :id")
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);
}
