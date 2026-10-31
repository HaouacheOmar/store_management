-- Sorties de stock d'une vente : la compensation n'est possible qu'en citant une réservation, une seule fois.
CREATE TABLE stock_reservation (
    id           UUID         PRIMARY KEY,
    store_id     BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('RESERVED','RELEASED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    released_at  TIMESTAMPTZ
);
CREATE INDEX ix_stock_reservation_created ON stock_reservation(created_at);

CREATE TABLE stock_reservation_line (
    reservation_id  UUID     NOT NULL REFERENCES stock_reservation(id) ON DELETE CASCADE,
    product_id      BIGINT   NOT NULL,
    qty             INTEGER  NOT NULL CHECK (qty > 0),
    PRIMARY KEY (reservation_id, product_id)
);
