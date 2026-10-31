CREATE TABLE sale (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- = n° de ticket
    store_id       BIGINT        NOT NULL,
    cashier_id     BIGINT        NOT NULL,
    cashier_name   VARCHAR(120)  NOT NULL,
    sale_date      DATE          NOT NULL,
    total          NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    cash_received  NUMERIC(12,2) NOT NULL,
    change_given   NUMERIC(12,2) NOT NULL CHECK (change_given >= 0),
    status         VARCHAR(10)   NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED','CANCELLED')),
    cancelled_by   BIGINT,
    cancelled_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (cash_received >= total),
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
CREATE INDEX ix_sale_store_day         ON sale(store_id, sale_date);
CREATE INDEX ix_sale_store_cashier_day ON sale(store_id, cashier_id, sale_date);

CREATE TABLE sale_line (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id           BIGINT        NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    product_id        BIGINT        NOT NULL,
    product_name      VARCHAR(120)  NOT NULL,
    category_id       BIGINT        NOT NULL,
    category_name     VARCHAR(80)   NOT NULL,
    qty               INTEGER       NOT NULL CHECK (qty > 0),
    unit_price        NUMERIC(12,2) NOT NULL,
    discount_percent  SMALLINT      NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100),
    line_total        NUMERIC(12,2) NOT NULL
);
CREATE INDEX ix_sale_line_sale ON sale_line(sale_id);

CREATE TABLE outbox_event (
    id          UUID         PRIMARY KEY,
    topic       VARCHAR(100) NOT NULL,
    msg_key     VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unsent ON outbox_event(created_at) WHERE sent_at IS NULL;
