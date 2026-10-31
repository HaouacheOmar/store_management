CREATE TABLE supplier (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    BIGINT        NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    phone       VARCHAR(30),
    email       VARCHAR(180),
    address     VARCHAR(255),
    deleted     BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_supplier_name ON supplier(store_id, lower(name)) WHERE NOT deleted;
