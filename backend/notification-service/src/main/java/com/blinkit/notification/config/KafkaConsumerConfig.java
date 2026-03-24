package com.blinkit.notification.config;

import com.blinkit.notification.event.InventoryLowEvent;
import com.blinkit.notification.event.UserPasswordResetEvent;
import com.blinkit.notification.event.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseProps() {
        return Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG,           "notification-service",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class
        );
    }

    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory() {
        JsonDeserializer<UserRegisteredEvent> deser = new JsonDeserializer<>(UserRegisteredEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> userRegisteredListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userRegisteredConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserPasswordResetEvent> userPasswordResetConsumerFactory() {
        JsonDeserializer<UserPasswordResetEvent> deser = new JsonDeserializer<>(UserPasswordResetEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserPasswordResetEvent> userPasswordResetListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserPasswordResetEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userPasswordResetConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, InventoryLowEvent> inventoryLowConsumerFactory() {
        JsonDeserializer<InventoryLowEvent> deser = new JsonDeserializer<>(InventoryLowEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(baseProps(), new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryLowEvent> inventoryLowListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryLowEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryLowConsumerFactory());
        return factory;
    }
}
