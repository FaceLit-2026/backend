package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.time.OffsetDateTime;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.FaceLit.backend.auth.model.security.AuthRateLimit;
import com.FaceLit.backend.auth.repository.security.AuthRateLimitRepository;
import com.FaceLit.backend.auth.exception.RecoverySecurityException;
import com.FaceLit.backend.auth.service.security.RecoverySecrets;

/** Durable fixed windows shared by all backend instances; identifiers are hashed. */
@Service
public class RecoveryRateLimiter {
    private final AuthRateLimitRepository buckets;
    private final TransactionTemplate transactions;
    private final int ipLimit;
    private final int accountLimit;
    public RecoveryRateLimiter(AuthRateLimitRepository buckets, PlatformTransactionManager manager,
            @Value("${security.recovery.ip-limit:30}") int ipLimit,
            @Value("${security.recovery.account-limit:10}") int accountLimit) {
        if (ipLimit < 1 || accountLimit < 1) throw new IllegalArgumentException("Rate limits must be positive");
        this.buckets = buckets; this.ipLimit = ipLimit; this.accountLimit = accountLimit;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void ip(String address) { acquire("ip:" + address, ipLimit, Duration.ofMinutes(5)); }
    public void account(String email, String operation) {
        String key = email.trim().toLowerCase(java.util.Locale.ROOT);
        if ("request".equals(operation)) {
            acquire("request-minute:" + key, 1, Duration.ofMinutes(1));
            acquire("request-window:" + key, 3, Duration.ofMinutes(15));
        } else acquire("validation:" + key, accountLimit, Duration.ofMinutes(15));
    }
    private void acquire(String identity, int limit, Duration window) {
        String key = RecoverySecrets.digest(identity);
        Long retry;
        try { retry = transactions.execute(tx -> increment(key, limit, window)); }
        catch (DataIntegrityViolationException race) {
            // Two instances can concurrently create the first bucket. Retry outside the failed transaction.
            retry = transactions.execute(tx -> increment(key, limit, window));
        }
        if (retry != null && retry > 0) throw new RecoverySecurityException(429,
                "Demasiados intentos. Espera antes de volver a intentar.", retry);
    }
    private long increment(String key, int limit, Duration window) {
        OffsetDateTime now = OffsetDateTime.now();
        AuthRateLimit bucket = buckets.lock(key).orElseGet(() -> {
            var created = new AuthRateLimit(); created.setKey(key); created.setWindowStart(now); return created;
        });
        if (!bucket.getWindowStart().plus(window).isAfter(now)) {
            bucket.setWindowStart(now); bucket.setAttempts(0);
        }
        if (bucket.getAttempts() >= limit)
            return Math.max(1, Duration.between(now, bucket.getWindowStart().plus(window)).toSeconds());
        bucket.setAttempts(bucket.getAttempts() + 1); buckets.saveAndFlush(bucket); return 0;
    }
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanup() {
        transactions.executeWithoutResult(tx -> buckets.deleteByWindowStartBefore(OffsetDateTime.now().minusHours(1)));
    }
}
