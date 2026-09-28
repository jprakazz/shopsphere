-- Indexes added after profiling the catalogue and order-history queries.
-- See the "Feature Development Process" section of the project report for before/after numbers.

-- Catalogue browsing: WHERE category_id = ? AND active = true ORDER BY price
CREATE INDEX idx_products_category_active_price ON products (category_id, active, price);

-- Name prefix search: WHERE name LIKE 'abc%'
CREATE INDEX idx_products_name ON products (name);

-- Order history: WHERE user_id = ? ORDER BY created_at DESC
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);

CREATE INDEX idx_payments_order ON payments (order_id);
