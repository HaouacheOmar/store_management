-- Lot 2 : catégories et produits. Remises, restockages et outbox : V2 (lot 3).
CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    BIGINT       NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_category_name ON category(store_id, lower(name)) WHERE NOT deleted;

CREATE TABLE product (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id         BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL REFERENCES category(id),
    name             VARCHAR(120)  NOT NULL,
    code             VARCHAR(50),
    price            NUMERIC(12,2) NOT NULL CHECK (price > 0),
    stock_qty        INTEGER       NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    alert_threshold  INTEGER       NOT NULL DEFAULT 5 CHECK (alert_threshold >= 0),
    deleted          BOOLEAN       NOT NULL DEFAULT false,
    version          INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_store_category ON product(store_id, category_id) WHERE NOT deleted;
CREATE UNIQUE INDEX ux_product_code ON product(store_id, code) WHERE code IS NOT NULL AND NOT deleted;
CREATE INDEX ix_product_low_stock ON product(store_id) WHERE NOT deleted AND stock_qty <= alert_threshold;
