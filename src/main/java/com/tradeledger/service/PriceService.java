package com.tradeledger.service;

import com.tradeledger.dto.response.PriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Service providing real-time market prices backed by a Redis read-through cache.
 * Implements PriceProvider with @Primary so that all dependent domain services
 * read through the cache by default.
 */
@Service
@Primary
public class PriceService implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final String PRICE_KEY_PREFIX = "price:";

    private final StringRedisTemplate redisTemplate;
    private final PriceProvider fallbackProvider;
    private final long cacheTtlSeconds;

    public PriceService(
            StringRedisTemplate redisTemplate,
            @Qualifier("simulatedPriceProvider") PriceProvider fallbackProvider,
            @Value("${app.price.cache-ttl-seconds:10}") long cacheTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.fallbackProvider = fallbackProvider;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Override
    public BigDecimal getPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        String cacheKey = PRICE_KEY_PREFIX + normalizedSymbol;

        // 1. Read-through cache lookup from Redis
        try {
            String cachedPrice = redisTemplate.opsForValue().get(cacheKey);
            if (cachedPrice != null) {
                return new BigDecimal(cachedPrice).setScale(4, RoundingMode.HALF_UP);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable for cache read on {}: {}. Falling back to provider.",
                    cacheKey, ex.getMessage());
        }

        // 2. Cache miss: Fetch from price provider
        BigDecimal freshPrice = fallbackProvider.getPrice(normalizedSymbol);
        if (freshPrice == null) {
            freshPrice = BigDecimal.ZERO;
        } else {
            freshPrice = freshPrice.setScale(4, RoundingMode.HALF_UP);
        }

        // 3. Populate Redis cache with TTL
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    freshPrice.toPlainString(),
                    Duration.ofSeconds(cacheTtlSeconds)
            );
        } catch (Exception ex) {
            log.warn("Failed to write price for {} to Redis: {}", normalizedSymbol, ex.getMessage());
        }

        return freshPrice;
    }

    /**
     * Retrieves the price wrapped in a response DTO with the current timestamp.
     */
    public PriceResponse getPriceResponse(String symbol) {
        String normalizedSymbol = symbol != null ? symbol.trim().toUpperCase() : "";
        BigDecimal price = getPrice(normalizedSymbol);
        return new PriceResponse(normalizedSymbol, price, Instant.now());
    }

    /**
     * Direct price update into Redis cache.
     */
    public void cachePrice(String symbol, BigDecimal price) {
        if (symbol == null || price == null) {
            return;
        }
        String normalizedSymbol = symbol.trim().toUpperCase();
        String cacheKey = PRICE_KEY_PREFIX + normalizedSymbol;
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    price.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    Duration.ofSeconds(cacheTtlSeconds)
            );
        } catch (Exception ex) {
            log.warn("Failed to cache price for {} in Redis: {}", normalizedSymbol, ex.getMessage());
        }
    }
}
