package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages per-symbol order books and concurrency locks.
 * Guarantees per-symbol serial matching with non-blocking concurrency across different symbols.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final OrderRepository orderRepository;
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();

    public MatchingEngine(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Rebuilds in-memory order books on application startup from active orders in the database.
     */
    @PostConstruct
    public void init() {
        List<Order> activeOrders = orderRepository.findAll().stream()
                .filter(Order::isActive)
                .toList();

        for (Order order : activeOrders) {
            OrderBook book = getOrCreateBook(order.getSymbol());
            book.addOrder(order);
        }
        log.info("Initialized MatchingEngine with {} active resting orders", activeOrders.size());
    }

    public OrderBook getOrCreateBook(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        return books.computeIfAbsent(normalizedSymbol, OrderBook::new);
    }

    public ReentrantLock getLock(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        return symbolLocks.computeIfAbsent(normalizedSymbol, k -> new ReentrantLock(true));
    }

    public List<OrderBook.Match> match(Order order) {
        OrderBook book = getOrCreateBook(order.getSymbol());
        return book.match(order);
    }

    public void addOrder(Order order) {
        OrderBook book = getOrCreateBook(order.getSymbol());
        book.addOrder(order);
    }

    public Order cancelOrder(String symbol, UUID orderId) {
        OrderBook book = getOrCreateBook(symbol);
        return book.cancelOrder(orderId);
    }

    public Order getOrder(String symbol, UUID orderId) {
        OrderBook book = getOrCreateBook(symbol);
        return book.getOrder(orderId);
    }
}

