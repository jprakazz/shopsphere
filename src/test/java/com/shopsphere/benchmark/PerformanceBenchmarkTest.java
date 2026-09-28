package com.shopsphere.benchmark;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopsphere.catalog.ProductRepository;
import com.shopsphere.catalog.ProductService;

/**
 * Measures the optimisations described in the project report. Not part of the normal build:
 * run with {@code ./mvnw test -Dsurefire.excludedGroups= -Dgroups=benchmark}.
 * Results are written to target/benchmark-results.md.
 */
@Tag("benchmark")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bench;MODE=MySQL;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1",
        "app.seed-sample-data=false",
        "app.orders.expiry-job-enabled=false",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceBenchmarkTest {

    private static final int PRODUCTS = 200_000;
    private static final int CATEGORIES = 50;
    private static final int WARMUP = 300;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager cacheManager;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired TransactionTemplate tx;

    private final StringBuilder report = new StringBuilder();

    @BeforeAll
    void seed() {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> cats = new ArrayList<>();
        for (int c = 1; c <= CATEGORIES; c++) {
            cats.add(new Object[] {"Category " + c});
        }
        jdbc.batchUpdate("insert into categories(name) values (?)", cats);
        List<Long> catIds = jdbc.queryForList("select id from categories order by id", Long.class);

        String[] words = {"Aurora", "Nimbus", "Pulse", "Vertex", "Zen", "Orbit", "Nova", "Lumen", "Terra", "Echo"};
        Random random = new Random(42);
        List<Object[]> rows = new ArrayList<>(10_000);
        for (int i = 1; i <= PRODUCTS; i++) {
            BigDecimal price = BigDecimal.valueOf(100 + random.nextInt(99_900), 0);
            rows.add(new Object[] {"SKU-" + i, words[random.nextInt(words.length)] + " Item " + i, price,
                    random.nextInt(100), true, catIds.get(random.nextInt(CATEGORIES)), 0L, now, now});
            if (rows.size() == 10_000) {
                insertProducts(rows);
                rows.clear();
            }
        }
        insertProducts(rows);

        report.append("# ShopSphere benchmark results\n\n")
                .append("- Run at: ").append(now.withNano(0)).append('\n')
                .append("- Machine: ").append(Runtime.getRuntime().availableProcessors()).append(" logical CPUs, Java ")
                .append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append('\n')
                .append("- Database: H2 2.x in-memory (MySQL mode), ").append(PRODUCTS).append(" products in ")
                .append(CATEGORIES).append(" categories\n")
                .append("- Cache: Spring in-process cache (production profile uses Redis)\n\n");
    }

    private void insertProducts(List<Object[]> rows) {
        jdbc.batchUpdate("""
                insert into products(sku, name, price, stock_quantity, active, category_id, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", rows);
    }

    // ---------------------------------------------------------------- 1. product detail cache

    @Test
    @Order(1)
    void productDetailWithAndWithoutCache() throws Exception {
        List<Long> ids = jdbc.queryForList("select id from products order by id limit 1000", Long.class);
        var cache = cacheManager.getCache(ProductService.PRODUCT_CACHE);
        int n = 5_000;

        IntFunction<Long> miss = i -> {
            long id = ids.get(i % ids.size());
            cache.evict(id);
            return timeGet(id);
        };
        IntFunction<Long> hit = i -> timeGet(ids.get(i % ids.size()));

        for (int i = 0; i < WARMUP; i++) {
            miss.apply(i);
            hit.apply(i);
        }
        Stats without = measure(n, miss);
        Stats with = measure(n, hit);

        report.append("## 1. GET /api/products/{id}: cache miss vs cache hit\n\n")
                .append(n).append(" requests each through the full MVC stack (MockMvc), 1,000 distinct products.\n\n")
                .append(Stats.header()).append(without.row("Cache miss (DB query + mapping)"))
                .append(with.row("Cache hit")).append('\n')
                .append(String.format("Mean latency reduced by %.1f%% (%.2fx faster).%n%n",
                        100 * (1 - with.mean / without.mean), without.mean / with.mean));
    }

    private long timeGet(long id) {
        try {
            long t = System.nanoTime();
            mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());
            return System.nanoTime() - t;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- 2. search indexes

    @Test
    @Order(2)
    void catalogueSearchWithAndWithoutIndexes() {
        List<Long> catIds = jdbc.queryForList("select id from categories order by id", Long.class);
        var pageable = PageRequest.of(0, 20, Sort.by("price"));
        int n = 300;

        IntFunction<Long> byCategory = i -> time(() -> productService.search(catIds.get(i % CATEGORIES), null,
                null, null, pageable));
        IntFunction<Long> byCategoryAndPrice = i -> time(() -> productService.search(catIds.get(i % CATEGORIES),
                null, new BigDecimal("5000"), new BigDecimal("6000"), pageable));
        // What a user types into a search box: specific enough to match a handful of rows.
        List<String> specific = jdbc.queryForList(
                "select substring(name, 1, length(name) - 1) from products where mod(id, 997) = 0", String.class);
        IntFunction<Long> byNameSpecific = i -> time(() -> productService.search(null,
                specific.get(i % specific.size()), null, null, PageRequest.of(0, 20, Sort.by("name"))));
        // A very broad prefix matching about 5% of the table.
        String[] broad = {"Aurora Item 1", "Nimbus Item 2", "Pulse Item 3", "Zen Item 4", "Echo Item 5"};
        IntFunction<Long> byNameBroad = i -> time(() -> productService.search(null, broad[i % broad.length],
                null, null, PageRequest.of(0, 20, Sort.by("name"))));

        // Measure without the indexes first so that JIT warm-up cannot favour the indexed run.
        jdbc.execute("drop index idx_products_category_active_price");
        jdbc.execute("drop index idx_products_name");
        String planWithout = plan();
        Stats[] without = runSearches(n, byCategory, byCategoryAndPrice, byNameSpecific, byNameBroad);

        jdbc.execute("create index idx_products_category_active_price on products (category_id, active, price)");
        jdbc.execute("create index idx_products_name on products (name)");
        String planWith = plan();
        Stats[] with = runSearches(n, byCategory, byCategoryAndPrice, byNameSpecific, byNameBroad);

        String[] labels = {"category, sort by price (~4,000 matches)", "category + price range 5000-6000 (~40 matches)",
                "specific name prefix (1-11 matches)", "broad name prefix (~11,000 matches)"};
        report.append("## 2. Catalogue search: without vs with composite indexes\n\n")
                .append(n).append(" searches per query shape (page of 20, includes the COUNT query).\n")
                .append("Note: H2 keeps its own index on the category_id foreign key even without our indexes.\n\n")
                .append("| Query | Without indexes: mean ms | With indexes: mean ms | Speed-up |\n|---|---:|---:|---:|\n");
        for (int q = 0; q < labels.length; q++) {
            report.append(String.format("| %s | %.2f | %.2f | %.1fx |%n", labels[q], without[q].mean, with[q].mean,
                    without[q].mean / with[q].mean));
        }
        report.append("\nQuery plan for `category + price range`, without indexes:\n\n```\n").append(planWithout)
                .append("\n```\n\nWith indexes:\n\n```\n").append(planWith).append("\n```\n\n");
    }

    private Stats[] runSearches(int n, IntFunction<Long>... queries) {
        Stats[] out = new Stats[queries.length];
        for (int q = 0; q < queries.length; q++) {
            for (int i = 0; i < 150; i++) {
                queries[q].apply(i);
            }
            out[q] = measure(n, queries[q]);
        }
        return out;
    }

    private String plan() {
        Long cat = jdbc.queryForObject("select min(id) from categories", Long.class);
        return jdbc.queryForObject("explain select * from products where category_id = " + cat
                + " and active = true and price between 5000 and 6000 order by price limit 20", String.class);
    }

    // ---------------------------------------------------------------- 3. stock reservation

    @Test
    @Order(3)
    void stockReservationStrategiesUnderContention() throws Exception {
        int buyers = 50;
        int stock = 10;
        report.append("## 3. Reserving stock under contention\n\n")
                .append(buyers).append(" concurrent buyers, ").append(stock)
                .append(" units in stock, each buys 1. Each strategy waits 2 ms between read and write to model ")
                .append("application work. Averages over 5 rounds.\n\n")
                .append("| Strategy | Orders accepted | Final stock | Oversold units | Mean wall time ms |\n")
                .append("|---|---:|---:|---:|---:|\n");

        Long catId = jdbc.queryForObject("select min(id) from categories", Long.class);
        String[] names = {"Naive read-check-write", "Pessimistic lock (SELECT ... FOR UPDATE)",
                "Atomic conditional UPDATE (ShopSphere)"};
        for (int s = 0; s < names.length; s++) {
            int strategy = s;
            int accepted = 0;
            int finalStock = 0;
            long totalNanos = 0;
            int rounds = 5;
            for (int r = 0; r < rounds; r++) {
                String sku = "RACE-" + s + "-" + r;
                jdbc.update("""
                        insert into products(sku, name, price, stock_quantity, active, category_id, version, created_at, updated_at)
                        values (?, 'Race', 10, ?, true, ?, 0, current_timestamp, current_timestamp)""", sku, stock, catId);
                long id = jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
                long t = System.nanoTime();
                accepted += race(buyers, () -> reserve(strategy, id));
                totalNanos += System.nanoTime() - t;
                finalStock += jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, id);
            }
            double acc = accepted / 5.0;
            double fin = finalStock / 5.0;
            report.append(String.format("| %s | %.1f | %.1f | %.1f | %.1f |%n", names[s], acc, fin,
                    Math.max(0, acc - stock), totalNanos / 5.0 / 1e6));
        }
        report.append('\n');
    }

    /** Returns true when the buyer got a unit. */
    private boolean reserve(int strategy, long id) {
        return Boolean.TRUE.equals(tx.execute(status -> switch (strategy) {
            case 0 -> {
                int current = jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, id);
                if (current < 1) {
                    yield false;
                }
                pause();
                jdbc.update("update products set stock_quantity = ? where id = ?", current - 1, id);
                yield true;
            }
            case 1 -> {
                int current = jdbc.queryForObject("select stock_quantity from products where id = ? for update",
                        Integer.class, id);
                if (current < 1) {
                    yield false;
                }
                pause();
                jdbc.update("update products set stock_quantity = stock_quantity - 1 where id = ?", id);
                yield true;
            }
            default -> {
                pause();
                yield productRepository.reserveStock(id, 1) == 1;
            }
        }));
    }

    private static void pause() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int race(int buyers, java.util.concurrent.Callable<Boolean> attempt) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    if (attempt.call()) {
                        ok.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : fs) {
                f.get();
            }
        }
        return ok.get();
    }

    // ---------------------------------------------------------------- helpers

    private static long time(Runnable r) {
        long t = System.nanoTime();
        r.run();
        return System.nanoTime() - t;
    }

    private static Stats measure(int n, IntFunction<Long> sample) {
        long[] nanos = new long[n];
        for (int i = 0; i < n; i++) {
            nanos[i] = sample.apply(i);
        }
        return Stats.of(nanos);
    }

    record Stats(double mean, double p50, double p95, double p99) {
        static Stats of(long[] nanos) {
            long[] s = nanos.clone();
            Arrays.sort(s);
            return new Stats(Arrays.stream(s).average().orElse(0) / 1e6, pct(s, 50), pct(s, 95), pct(s, 99));
        }

        private static double pct(long[] sorted, int p) {
            return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1)] / 1e6;
        }

        static String header() {
            return "| Scenario | Mean ms | p50 ms | p95 ms | p99 ms |\n|---|---:|---:|---:|---:|\n";
        }

        String row(String label) {
            return String.format("| %s | %.3f | %.3f | %.3f | %.3f |%n", label, mean, p50, p95, p99);
        }
    }

    @AfterAll
    void write() throws IOException {
        Path out = Path.of("target", "benchmark-results.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }
}
