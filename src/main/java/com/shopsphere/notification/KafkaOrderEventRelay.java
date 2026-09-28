package com.shopsphere.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

import com.shopsphere.config.AppProperties;
import com.shopsphere.order.OrderEvents.OrderEvent;

import tools.jackson.databind.ObjectMapper;

/**
 * Production event path: order events are published to a Kafka topic after commit and consumed
 * by the notification listener. Keying by order id keeps every event of one order in one partition,
 * so consumers see them in order. Other services (analytics, warehouse) can subscribe independently.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaOrderEventRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventRelay.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final NotificationService notificationService;
    private final String topic;

    public KafkaOrderEventRelay(KafkaTemplate<String, String> kafka, ObjectMapper json,
                                NotificationService notificationService, AppProperties props) {
        this.kafka = kafka;
        this.json = json;
        this.notificationService = notificationService;
        this.topic = props.kafka().orderTopic();
    }

    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @TransactionalEventListener
    public void publish(OrderEvent event) {
        kafka.send(topic, String.valueOf(event.orderId()), json.writeValueAsString(event))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}", event.type(), event.orderNumber(), ex);
                    }
                });
    }

    @KafkaListener(topics = "${app.kafka.order-topic}", groupId = "shopsphere-notifications")
    public void consume(String payload) {
        notificationService.notify(json.readValue(payload, OrderEvent.class));
    }
}
