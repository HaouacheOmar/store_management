-- Modèle de lecture alimenté par Kafka (sale.completed / sale.cancelled). Reconstructible, jamais source de vérité.
-- Notifications : V2 (lot 6).
CREATE TABLE daily_product_sales (
    store_id       BIGINT        NOT NULL,
    sale_date      DATE          NOT NULL,
    product_id     BIGINT        NOT NULL,
    product_name   VARCHAR(120)  NOT NULL,
    category_id    BIGINT        NOT NULL,
    category_name  VARCHAR(80)   NOT NULL,
    qty            INTEGER       NOT NULL DEFAULT 0,
    revenue        NUMERIC(14,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, sale_date, product_id)
);
CREATE INDEX ix_dps_category ON daily_product_sales(store_id, category_id, sale_date);

CREATE TABLE daily_cashier_sales (
    store_id      BIGINT        NOT NULL,
    sale_date     DATE          NOT NULL,
    cashier_id    BIGINT        NOT NULL,
    cashier_name  VARCHAR(120)  NOT NULL,
    ticket_count  INTEGER       NOT NULL DEFAULT 0,
    total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, sale_date, cashier_id)
);

CREATE TABLE processed_event (
    event_id      UUID        PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
