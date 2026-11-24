package com.tradeledger.repository;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByPortfolioId(UUID portfolioId);

    List<Order> findByPortfolioIdAndStatus(UUID portfolioId, OrderStatus status);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findBySymbolAndStatusIn(String symbol, List<OrderStatus> statuses);
}

