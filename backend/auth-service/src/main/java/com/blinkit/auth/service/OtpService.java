package com.blinkit.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redis;
    private static final String OTP_PREFIX = "otp:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${otp.expiry-minutes:5}")
    private long otpExpiryMinutes;

    public String generateAndStore(String email) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(OTP_PREFIX + email, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        return otp;
    }

    public boolean validate(String email, String otp) {
        String stored = redis.opsForValue().get(OTP_PREFIX + email);
        if (stored != null && stored.equals(otp)) {
            redis.delete(OTP_PREFIX + email);
            return true;
        }
        return false;
    }
}
