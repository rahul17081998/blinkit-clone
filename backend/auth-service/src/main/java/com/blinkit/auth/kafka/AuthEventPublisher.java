package com.blinkit.auth.kafka;

import com.blinkit.auth.event.UserPasswordResetEvent;
import com.blinkit.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String TOPIC_USER_REGISTERED      = "user.registered";
    public static final String TOPIC_USER_PASSWORD_RESET  = "user.password.reset";

    public void publishUserRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(TOPIC_USER_REGISTERED, event.getUserId(), event);
        log.info("Published {} for userId={}", TOPIC_USER_REGISTERED, event.getUserId());
    }

    public void publishUserPasswordReset(UserPasswordResetEvent event) {
        kafkaTemplate.send(TOPIC_USER_PASSWORD_RESET, event.getUserId(), event);
        log.info("Published {} for userId={}", TOPIC_USER_PASSWORD_RESET, event.getUserId());
    }
}
