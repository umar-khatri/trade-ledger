package com.tradeledger.service;

import com.tradeledger.dto.response.PriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PriceProvider fallbackProvider;

    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new PriceService(redisTemplate, fallbackProvider, 10);
    }

    @Test
    void getPrice_whenCachedInRedis_shouldReturnCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("price:AAPL")).thenReturn("188.5000");

        BigDecimal price = priceService.getPrice("AAPL");

        assertThat(price).isEqualByComparingTo(new BigDecimal("188.5000"));
        verify(fallbackProvider, never()).getPrice(any());
    }

    @Test
    void getPrice_whenCacheMiss_shouldFetchFromProviderAndStoreInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("price:GOOGL")).thenReturn(null);
        when(fallbackProvider.getPrice("GOOGL")).thenReturn(new BigDecimal("175.0000"));

        BigDecimal price = priceService.getPrice("GOOGL");

        assertThat(price).isEqualByComparingTo(new BigDecimal("175.0000"));
        verify(valueOperations).set(eq("price:GOOGL"), eq("175.0000"), eq(Duration.ofSeconds(10)));
    }

    @Test
    void getPrice_whenRedisThrowsException_shouldFallbackGracefully() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        when(fallbackProvider.getPrice("MSFT")).thenReturn(new BigDecimal("420.0000"));

        BigDecimal price = priceService.getPrice("MSFT");

        assertThat(price).isEqualByComparingTo(new BigDecimal("420.0000"));
    }

    @Test
    void getPriceResponse_shouldIncludeTimestampAndSymbol() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("price:TSLA")).thenReturn("240.0000");

        PriceResponse response = priceService.getPriceResponse("TSLA");

        assertThat(response.symbol()).isEqualTo("TSLA");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("240.0000"));
        assertThat(response.timestamp()).isNotNull();
    }
}
