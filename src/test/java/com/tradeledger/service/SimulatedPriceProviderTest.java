package com.tradeledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPriceProviderTest {

    private SimulatedPriceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SimulatedPriceProvider();
        ReflectionTestUtils.setField(provider, "symbolsConfig", "AAPL,GOOGL,MSFT");
        provider.init();
    }

    @Test
    void init_shouldInitializeConfiguredSymbols() {
        Map<String, BigDecimal> allPrices = provider.getAllPrices();
        assertThat(allPrices).containsKeys("AAPL", "GOOGL", "MSFT");
        assertThat(allPrices.get("AAPL")).isEqualByComparingTo(new BigDecimal("185.5000"));
    }

    @Test
    void getPrice_shouldReturnDefaultIfUnlisted() {
        BigDecimal price = provider.getPrice("XYZ");
        assertThat(price).isNotNull();
        assertThat(price).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void getPrice_caseInsensitive() {
        BigDecimal p1 = provider.getPrice("aapl");
        BigDecimal p2 = provider.getPrice("AAPL");
        assertThat(p1).isEqualByComparingTo(p2);
    }

    @Test
    void tickPrices_shouldUpdatePricesWithinReasonableBounds() {
        BigDecimal initialPrice = provider.getPrice("AAPL");
        provider.tickPrices();
        BigDecimal nextPrice = provider.getPrice("AAPL");

        assertThat(nextPrice).isNotNull();
        assertThat(nextPrice).isGreaterThan(BigDecimal.ZERO);
        // Step is within -1.5% and +1.5%
        BigDecimal minExpected = initialPrice.multiply(new BigDecimal("0.97"));
        BigDecimal maxExpected = initialPrice.multiply(new BigDecimal("1.03"));
        assertThat(nextPrice).isBetween(minExpected, maxExpected);
    }

    @Test
    void setPrice_shouldOverridePrice() {
        provider.setPrice("AAPL", new BigDecimal("200.0000"));
        assertThat(provider.getPrice("AAPL")).isEqualByComparingTo(new BigDecimal("200.0000"));
    }
}
