package com.tradeledger.service;

import java.math.BigDecimal;

/**
 * Provides current market prices for symbols. Implementations may be
 * simulated (random walk) or backed by a real market data feed.
 */
public interface PriceProvider {

    /**
     * Returns the latest price for the given symbol, or null if unknown.
     */
    BigDecimal getPrice(String symbol);
}

