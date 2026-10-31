CREATE TABLE store (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'Africa/Algiers',
    currency    VARCHAR(3)   NOT NULL DEFAULT 'DZD',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id       BIGINT       NOT NULL REFERENCES store(id),
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(180) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN','MANAGER','GUEST')),
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (store_id, email)
);
CREATE UNIQUE INDEX ux_one_admin_per_store ON app_user(store_id) WHERE role = 'ADMIN';
CREATE UNIQUE INDEX ux_user_email ON app_user(lower(email));

CREATE TABLE user_permission (
    user_id     BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    permission  VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, permission)
);
