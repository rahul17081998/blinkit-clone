package com.blinkit.user.config;

import com.blinkit.user.event.UserDeletedEvent;
import com.blinkit.user.event.UserRegisteredEvent;
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

    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory() {
        JsonDeserializer<UserRegisteredEvent> deser = new JsonDeserializer<>(UserRegisteredEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,         bootstrapServers,
                    ConsumerConfig.GROUP_ID_CONFIG,                  "user-service",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,         "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,    StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,  JsonDeserializer.class
                ),
                new StringDeserializer(),
                deser
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> userRegisteredListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userRegisteredConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserDeletedEvent> userDeletedConsumerFactory() {
        JsonDeserializer<UserDeletedEvent> deser = new JsonDeserializer<>(UserDeletedEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,         bootstrapServers,
                    ConsumerConfig.GROUP_ID_CONFIG,                  "user-service",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,         "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,    StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,  JsonDeserializer.class
                ),
                new StringDeserializer(),
                deser
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent> userDeletedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userDeletedConsumerFactory());
        return factory;
    }
}
