package com.tradeledger.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates simulated market prices with a random-walk algorithm
 * for a configurable list of symbols.
 */
@Component("simulatedPriceProvider")
public class SimulatedPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPriceProvider.class);

    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();

    @Value("${app.price.symbols:AAPL,GOOGL,MSFT,AMZN,TSLA,META,NVDA,JPM,V,JNJ}")
    private String symbolsConfig;

    private static final Map<String, BigDecimal> DEFAULT_BASE_PRICES = Map.of(
            "AAPL", new BigDecimal("185.5000"),
            "GOOGL", new BigDecimal("175.2000"),
            "MSFT", new BigDecimal("420.7500"),
            "AMZN", new BigDecimal("186.4000"),
            "TSLA", new BigDecimal("242.1000"),
            "META", new BigDecimal("503.6000"),
            "NVDA", new BigDecimal("128.3000"),
            "JPM", new BigDecimal("198.8000"),
            "V", new BigDecimal("276.5000"),
            "JNJ", new BigDecimal("154.2000")
    );

    @PostConstruct
    public void init() {
        List<String> symbols = Arrays.stream(symbolsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        for (String symbol : symbols) {
            BigDecimal base = DEFAULT_BASE_PRICES.getOrDefault(symbol.toUpperCase(), new BigDecimal("100.0000"));
            currentPrices.put(symbol.toUpperCase(), base);
        }
        log.info("Initialized simulated prices for {} symbols: {}", currentPrices.size(), currentPrices.keySet());
    }

    @Override
    public BigDecimal getPrice(String symbol) {
        if (symbol == null) {
            return null;
        }
        String upperSymbol = symbol.trim().toUpperCase();
        return currentPrices.computeIfAbsent(upperSymbol, s ->
                DEFAULT_BASE_PRICES.getOrDefault(s, new BigDecimal("100.0000")));
    }

    /**
     * Periodically updates prices using a bounded random-walk step.
     */
    @Scheduled(fixedRateString = "${app.price.update-interval-ms:2000}")
    public void tickPrices() {
        for (Map.Entry<String, BigDecimal> entry : currentPrices.entrySet()) {
            BigDecimal oldPrice = entry.getValue();
            BigDecimal newPrice = calculateNextPrice(oldPrice);
            entry.setValue(newPrice);
        }
    }

    public void setPrice(String symbol, BigDecimal price) {
        if (symbol != null && price != null) {
            currentPrices.put(symbol.trim().toUpperCase(), price.setScale(4, RoundingMode.HALF_UP));
        }
    }

    /**
     * Random walk formula: Next Price = Price * (1 + delta)
     * where delta is chosen uniformly from [-1.5%, +1.5%].
     * Enforces a minimum price floor of 1.0000.
     */
    BigDecimal calculateNextPrice(BigDecimal currentPrice) {
        double pctChange = (ThreadLocalRandom.current().nextDouble() * 0.03) - 0.015; // -1.5% to +1.5%
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(pctChange));
        BigDecimal nextPrice = currentPrice.multiply(factor).setScale(4, RoundingMode.HALF_UP);

        if (nextPrice.compareTo(BigDecimal.ONE) < 0) {
            return new BigDecimal("1.0000");
        }
        return nextPrice;
    }

    public Map<String, BigDecimal> getAllPrices() {
        return Map.copyOf(currentPrices);
    }
}

