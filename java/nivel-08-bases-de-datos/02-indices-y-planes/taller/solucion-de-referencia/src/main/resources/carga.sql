-- Taller n8-02: un millón de pedidos generados.
DROP TABLE IF EXISTS pedidos;
CREATE TABLE pedidos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id  BIGINT        NOT NULL,
    correo      VARCHAR(160)  NOT NULL,
    estado      VARCHAR(20)   NOT NULL,
    canal       VARCHAR(20)   NOT NULL,
    total       NUMERIC(12,2) NOT NULL,
    creado_en   TIMESTAMPTZ   NOT NULL
);
INSERT INTO pedidos (cliente_id, correo, estado, canal, total, creado_en)
SELECT c,
       'Cliente' || c || '@Correo.co',
       CASE WHEN r < 0.02 THEN 'PENDIENTE' WHEN r < 0.10 THEN 'PAGADO' WHEN r < 0.30 THEN 'ENVIADO' ELSE 'ENTREGADO' END,
       CASE WHEN g % 3 = 0 THEN 'TIENDA' ELSE 'WEB' END,
       round((10000 + random() * 490000)::numeric, 2),
       timestamptz '2026-11-15 00:00:00-05' - (g * interval '63 seconds')   -- dos años hacia atrás, sin huecos
FROM (SELECT g, 1 + (hashint4(g) & 2147483647) % 100000 AS c, random() AS r
      FROM generate_series(1, 1000000) g) s;
ANALYZE pedidos;
