CREATE TABLE urls (
    codigo     VARCHAR(16) PRIMARY KEY,
    url_larga  TEXT        NOT NULL,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en  TIMESTAMPTZ,
    clics      BIGINT      NOT NULL DEFAULT 0
);
-- El coordinador de rangos: cada nextval entrega un bloque entero de números a una instancia.
CREATE SEQUENCE rangos START 1;
