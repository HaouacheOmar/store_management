-- Lot 6 : alertes de stock (stock.low / stock.restocked) et statut lu par utilisateur.
CREATE TABLE notification (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id      BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    product_name  VARCHAR(120)  NOT NULL,
    type          VARCHAR(5)    NOT NULL CHECK (type IN ('LOW','OUT')),
    stock_qty     INTEGER       NOT NULL,
    resolved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- une seule notification ouverte par produit et par type
CREATE UNIQUE INDEX ux_notification_open ON notification(store_id, product_id, type) WHERE resolved_at IS NULL;
CREATE INDEX ix_notification_store ON notification(store_id, created_at DESC);

-- ligne présente = lue par cet utilisateur
CREATE TABLE notification_read (
    notification_id  BIGINT      NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL,
    read_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, user_id)
);
