package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory order book for a single symbol providing price-time priority matching.
 * Bids are sorted descending (highest bid first); asks are sorted ascending (lowest ask first).
 * Orders at the same price level are executed in FIFO (time priority) order.
 */
public class OrderBook {

    private final String symbol;

    // Bids: highest price first
    private final NavigableMap<BigDecimal, Queue<Order>> bids = new TreeMap<>(Comparator.reverseOrder());

    // Asks: lowest price first
    private final NavigableMap<BigDecimal, Queue<Order>> asks = new TreeMap<>();

    // Map for O(1) order lookup and cancellation
    private final Map<UUID, Order> activeOrders = new ConcurrentHashMap<>();

    public record Match(
            Order restingOrder,
            BigDecimal matchPrice,
            BigDecimal matchQuantity
    ) {
    }

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Matches an incoming order against the resting book and returns all generated matches.
     */
    public synchronized List<Match> match(Order incomingOrder) {
        List<Match> matches = new ArrayList<>();
        BigDecimal incomingRemaining = incomingOrder.getRemainingQuantity();

        if (incomingRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            return matches;
        }

        if (incomingOrder.getSide() == OrderSide.BUY) {
            matchBuy(incomingOrder, matches);
        } else {
            matchSell(incomingOrder, matches);
        }

        return matches;
    }

    private void matchBuy(Order incomingBuy, List<Match> matches) {
        Iterator<Map.Entry<BigDecimal, Queue<Order>>> askIterator = asks.entrySet().iterator();

        while (askIterator.hasNext() && incomingBuy.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Map.Entry<BigDecimal, Queue<Order>> entry = askIterator.next();
            BigDecimal askPrice = entry.getKey();

            // For limit buy: stop if ask price exceeds our limit price
            if (incomingBuy.getType() == OrderType.LIMIT) {
                if (incomingBuy.getLimitPrice() != null && askPrice.compareTo(incomingBuy.getLimitPrice()) > 0) {
                    break;
                }
            }

            Queue<Order> queue = entry.getValue();
            Iterator<Order> queueIterator = queue.iterator();

            while (queueIterator.hasNext() && incomingBuy.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                Order restingAsk = queueIterator.next();

                // Prevent self-trade against the same portfolio
                if (restingAsk.getPortfolioId().equals(incomingBuy.getPortfolioId())) {
                    continue;
                }

                BigDecimal matchQty = incomingBuy.getRemainingQuantity().min(restingAsk.getRemainingQuantity());
                if (matchQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                matches.add(new Match(restingAsk, askPrice, matchQty));
                incomingBuy.fill(matchQty);
                restingAsk.fill(matchQty);

                if (restingAsk.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    queueIterator.remove();
                    activeOrders.remove(restingAsk.getId());
                }
            }

            if (queue.isEmpty()) {
                askIterator.remove();
            }
        }
    }

    private void matchSell(Order incomingSell, List<Match> matches) {
        Iterator<Map.Entry<BigDecimal, Queue<Order>>> bidIterator = bids.entrySet().iterator();

        while (bidIterator.hasNext() && incomingSell.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Map.Entry<BigDecimal, Queue<Order>> entry = bidIterator.next();
            BigDecimal bidPrice = entry.getKey();

            // For limit sell: stop if bid price is lower than our limit price
            if (incomingSell.getType() == OrderType.LIMIT) {
                if (incomingSell.getLimitPrice() != null && bidPrice.compareTo(incomingSell.getLimitPrice()) < 0) {
                    break;
                }
            }

            Queue<Order> queue = entry.getValue();
            Iterator<Order> queueIterator = queue.iterator();

            while (queueIterator.hasNext() && incomingSell.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                Order restingBid = queueIterator.next();

                // Prevent self-trade against the same portfolio
                if (restingBid.getPortfolioId().equals(incomingSell.getPortfolioId())) {
                    continue;
                }

                BigDecimal matchQty = incomingSell.getRemainingQuantity().min(restingBid.getRemainingQuantity());
                if (matchQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                matches.add(new Match(restingBid, bidPrice, matchQty));
                incomingSell.fill(matchQty);
                restingBid.fill(matchQty);

                if (restingBid.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    queueIterator.remove();
                    activeOrders.remove(restingBid.getId());
                }
            }

            if (queue.isEmpty()) {
                bidIterator.remove();
            }
        }
    }

    /**
     * Adds an unfilled limit order to the order book.
     */
    public synchronized void addOrder(Order order) {
        if (order.getType() != OrderType.LIMIT || order.getLimitPrice() == null) {
            return; // Market orders never rest in the book
        }
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        activeOrders.put(order.getId(), order);
        BigDecimal price = order.getLimitPrice();

        if (order.getSide() == OrderSide.BUY) {
            bids.computeIfAbsent(price, k -> new LinkedList<>()).add(order);
        } else {
            asks.computeIfAbsent(price, k -> new LinkedList<>()).add(order);
        }
    }

    /**
     * Cancels and removes an order from the book by ID.
     */
    public synchronized Order cancelOrder(UUID orderId) {
        Order order = activeOrders.remove(orderId);
        if (order == null) {
            return null;
        }

        order.cancel();
        BigDecimal price = order.getLimitPrice();
        if (price != null) {
            if (order.getSide() == OrderSide.BUY) {
                Queue<Order> queue = bids.get(price);
                if (queue != null) {
                    queue.removeIf(o -> o.getId().equals(orderId));
                    if (queue.isEmpty()) {
                        bids.remove(price);
                    }
                }
            } else {
                Queue<Order> queue = asks.get(price);
                if (queue != null) {
                    queue.removeIf(o -> o.getId().equals(orderId));
                    if (queue.isEmpty()) {
                        asks.remove(price);
                    }
                }
            }
        }
        return order;
    }

    public synchronized Order getOrder(UUID orderId) {
        return activeOrders.get(orderId);
    }

    public synchronized BigDecimal getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public synchronized BigDecimal getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public synchronized int getActiveOrderCount() {
        return activeOrders.size();
    }
}
