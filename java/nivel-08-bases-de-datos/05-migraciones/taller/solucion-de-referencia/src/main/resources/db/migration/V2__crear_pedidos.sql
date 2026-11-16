CREATE TABLE pedidos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id  BIGINT        NOT NULL REFERENCES clientes(id),
    total       NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    creado_en   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedidos_cliente ON pedidos (cliente_id);
