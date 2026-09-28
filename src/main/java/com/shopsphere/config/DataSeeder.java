package com.shopsphere.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.catalog.Category;
import com.shopsphere.catalog.CategoryRepository;
import com.shopsphere.catalog.Product;
import com.shopsphere.catalog.ProductRepository;
import com.shopsphere.user.Role;
import com.shopsphere.user.User;
import com.shopsphere.user.UserRepository;

/** Creates the admin account on first start and, in development, a small sample catalogue. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataSeeder(UserRepository users, CategoryRepository categories, ProductRepository products,
                      PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        if (props.seedSampleData() && categories.count() == 0) {
            seedCatalogue();
        }
    }

    private void seedAdmin() {
        var admin = props.admin();
        if (admin.password() == null || admin.password().isBlank() || users.existsByEmailIgnoreCase(admin.email())) {
            return;
        }
        users.save(new User("Store Admin", admin.email(), passwordEncoder.encode(admin.password()), null, Role.ADMIN));
        log.info("Created admin account {}", admin.email());
    }

    private void seedCatalogue() {
        Category electronics = categories.save(new Category("Electronics", "Phones, laptops and accessories"));
        Category books = categories.save(new Category("Books", "Fiction, non-fiction and technical books"));
        Category home = categories.save(new Category("Home & Kitchen", "Appliances and kitchenware"));
        products.saveAll(List.of(
                new Product("EL-PHONE-001", "Aurora X1 Smartphone", "6.5\" AMOLED, 128 GB", new BigDecimal("24999.00"), 50, null, electronics),
                new Product("EL-LAPTOP-001", "Nimbus 14 Laptop", "14\" FHD, 16 GB RAM, 512 GB SSD", new BigDecimal("58990.00"), 20, null, electronics),
                new Product("EL-EARBUD-001", "Pulse Wireless Earbuds", "ANC, 30h battery", new BigDecimal("3499.00"), 200, null, electronics),
                new Product("BK-DDIA-001", "Designing Data-Intensive Applications", "Martin Kleppmann", new BigDecimal("1899.00"), 75, null, books),
                new Product("BK-CLEAN-001", "Clean Architecture", "Robert C. Martin", new BigDecimal("899.00"), 60, null, books),
                new Product("HK-KETTLE-001", "Steel Electric Kettle 1.5L", "1500 W, auto shut-off", new BigDecimal("1299.00"), 120, null, home),
                new Product("HK-MIXER-001", "PowerMix Mixer Grinder", "750 W, 3 jars", new BigDecimal("3899.00"), 40, null, home)));
        log.info("Seeded sample catalogue");
    }
}
