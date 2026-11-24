-- Orders by portfolio + status: covers the most common query —
-- "list all active orders for portfolio X" and "list orders filtered by status".
CREATE INDEX idx_orders_portfolio_status ON orders(portfolio_id, status);

-- Positions by portfolio: used by position listing, portfolio valuation,
-- and the rebalance algorithm which reads all positions for a portfolio.
CREATE INDEX idx_position_portfolio ON position(portfolio_id);

-- Trades by symbol + time (descending): supports the trade-history query
-- which always returns trades ordered by most-recent first.
CREATE INDEX idx_trade_symbol_time ON trade(symbol, executed_at DESC);

-- Trades by buy/sell order: used to look up trades that belong to a
-- specific order, which is needed when listing trades for a portfolio
-- (join through the order's portfolio_id).
CREATE INDEX idx_trade_buy_order ON trade(buy_order_id);
CREATE INDEX idx_trade_sell_order ON trade(sell_order_id);

-- Target allocations by portfolio: the rebalance algorithm reads
-- all target weights for a portfolio in one query.
CREATE INDEX idx_target_alloc_portfolio ON target_allocation(portfolio_id);

-- Note: orders.idempotency_key already has an implicit B-tree index
-- from the UNIQUE constraint, so no extra index is needed there.
