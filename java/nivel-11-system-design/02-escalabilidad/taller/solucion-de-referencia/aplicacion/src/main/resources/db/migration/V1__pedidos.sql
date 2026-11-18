CREATE TABLE pedidos (
    id         BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT         NOT NULL,
    total      NUMERIC(12, 2) NOT NULL,
    creado_en  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
