-- Lot 3 : remises, restockages, outbox des événements Kafka.
CREATE TABLE discount (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id     BIGINT       NOT NULL,
    label        VARCHAR(120) NOT NULL,
    percent      SMALLINT     NOT NULL CHECK (percent BETWEEN 1 AND 100),
    target_type  VARCHAR(10)  NOT NULL CHECK (target_type IN ('PRODUCT','CATEGORY')),
    target_id    BIGINT       NOT NULL,
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ  NOT NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_discount_lookup ON discount(store_id, target_type, target_id, starts_at, ends_at);

CREATE TABLE restock (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id       BIGINT        NOT NULL,
    product_id     BIGINT        NOT NULL REFERENCES product(id),
    supplier_id    BIGINT        NOT NULL,
    supplier_name  VARCHAR(120)  NOT NULL,
    qty            INTEGER       NOT NULL CHECK (qty > 0),
    unit_cost      NUMERIC(12,2) CHECK (unit_cost >= 0),
    user_id        BIGINT        NOT NULL,
    user_name      VARCHAR(120)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_restock_product  ON restock(store_id, product_id, created_at DESC);
CREATE INDEX ix_restock_supplier ON restock(store_id, supplier_id, created_at DESC);

CREATE TABLE outbox_event (
    id          UUID         PRIMARY KEY,
    topic       VARCHAR(100) NOT NULL,
    msg_key     VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unsent ON outbox_event(created_at) WHERE sent_at IS NULL;
