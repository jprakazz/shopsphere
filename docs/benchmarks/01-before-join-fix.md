# ShopSphere benchmark results

- Run at: 2026-09-28T12:29:48
- Machine: 12 logical CPUs, Java 21.0.2, Windows 11
- Database: H2 2.x in-memory (MySQL mode), 200000 products in 50 categories
- Cache: Spring in-process cache (production profile uses Redis)

## 1. GET /api/products/{id}: cache miss vs cache hit

5000 requests each through the full MVC stack (MockMvc), 1,000 distinct products.

| Scenario | Mean ms | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|
| Cache miss (DB query + mapping) | 1.274 | 0.938 | 3.062 | 4.237 |
| Cache hit | 0.224 | 0.195 | 0.330 | 0.490 |

Mean latency reduced by 82.4% (5.69x faster).

## 2. Catalogue search: without vs with composite indexes

300 searches per query shape (page of 20, includes the COUNT query).
Note: H2 keeps its own index on the category_id foreign key even without our indexes.

| Query | Without indexes: mean ms | With indexes: mean ms | Speed-up |
|---|---:|---:|---:|
| category, sort by price (~4,000 matches) | 8.82 | 7.70 | 1.1x |
| category + price range 5000-6000 (~40 matches) | 6.92 | 0.72 | 9.6x |
| specific name prefix (1-11 matches) | 133.65 | 177.84 | 0.8x |
| broad name prefix (~11,000 matches) | 160.01 | 181.91 | 0.9x |

Query plan for `category + price range`, without indexes:

```
SELECT
    "public"."products"."id",
    "public"."products"."sku",
    "public"."products"."name",
    "public"."products"."description",
    "public"."products"."price",
    "public"."products"."stock_quantity",
    "public"."products"."image_url",
    "public"."products"."active",
    "public"."products"."category_id",
    "public"."products"."version",
    "public"."products"."created_at",
    "public"."products"."updated_at"
FROM "public"."products"
    /* public.fk_products_category_INDEX_C: category_id = CAST(1 AS BIGINT) */
WHERE ("price" BETWEEN 5000 AND 6000)
    AND ("category_id" = CAST(1 AS BIGINT))
    AND ("active" = TRUE)
ORDER BY 5
FETCH FIRST 20 ROWS ONLY
```

With indexes:

```
SELECT
    "public"."products"."id",
    "public"."products"."sku",
    "public"."products"."name",
    "public"."products"."description",
    "public"."products"."price",
    "public"."products"."stock_quantity",
    "public"."products"."image_url",
    "public"."products"."active",
    "public"."products"."category_id",
    "public"."products"."version",
    "public"."products"."created_at",
    "public"."products"."updated_at"
FROM "public"."products"
    /* public.idx_products_category_active_price: price >= 5000
        AND price <= 6000
        AND category_id = CAST(1 AS BIGINT)
        AND active = TRUE
     */
WHERE ("price" BETWEEN 5000 AND 6000)
    AND ("category_id" = CAST(1 AS BIGINT))
    AND ("active" = TRUE)
ORDER BY 5
FETCH FIRST 20 ROWS ONLY
```

## 3. Reserving stock under contention

50 concurrent buyers, 10 units in stock, each buys 1. Each strategy waits 2 ms between read and write to model application work. Averages over 5 rounds.

| Strategy | Orders accepted | Final stock | Oversold units | Mean wall time ms |
|---|---:|---:|---:|---:|
| Naive read-check-write | 50.0 | 4.8 | 40.0 | 31.3 |
| Pessimistic lock (SELECT ... FOR UPDATE) | 10.0 | 0.0 | 0.0 | 34.3 |
| Atomic conditional UPDATE (ShopSphere) | 10.0 | 0.0 | 0.0 | 37.8 |

