CREATE TABLE portfolio (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    cash_balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE position (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolio(id),
    symbol          VARCHAR(20) NOT NULL,
    quantity        NUMERIC(19,4) NOT NULL DEFAULT 0,
    avg_cost        NUMERIC(19,4) NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (portfolio_id, symbol)
);

CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id      UUID NOT NULL REFERENCES portfolio(id),
    symbol            VARCHAR(20) NOT NULL,
    side              VARCHAR(4) NOT NULL,
    type              VARCHAR(6) NOT NULL,
    quantity          NUMERIC(19,4) NOT NULL,
    limit_price       NUMERIC(19,4),
    filled_quantity   NUMERIC(19,4) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'NEW',
    idempotency_key   VARCHAR(64) UNIQUE,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE trade (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buy_order_id    UUID NOT NULL REFERENCES orders(id),
    sell_order_id   UUID NOT NULL REFERENCES orders(id),
    symbol          VARCHAR(20) NOT NULL,
    price           NUMERIC(19,4) NOT NULL,
    quantity        NUMERIC(19,4) NOT NULL,
    executed_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE target_allocation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolio(id),
    symbol          VARCHAR(20) NOT NULL,
    target_weight   NUMERIC(7,6) NOT NULL,
    UNIQUE (portfolio_id, symbol)
);
