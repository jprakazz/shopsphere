package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.shopsphere.notification.InProcessOrderEventRelay;
import com.shopsphere.notification.KafkaOrderEventRelay;

/** With Kafka enabled the Kafka relay replaces the in-process one (no broker is contacted at startup). */
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.orders.expiry-job-enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
class KafkaWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void kafkaRelayIsActiveAndInProcessRelayIsNot() {
        assertThat(context.getBeansOfType(KafkaOrderEventRelay.class)).hasSize(1);
        assertThat(context.getBeansOfType(InProcessOrderEventRelay.class)).isEmpty();
    }
}
