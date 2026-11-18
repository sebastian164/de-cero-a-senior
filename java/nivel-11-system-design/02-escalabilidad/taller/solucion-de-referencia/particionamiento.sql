-- Punto 4: mensajes de un chat particionados por hash del chat. La consulta por chat toca una partición;
-- la consulta "todos los mensajes de un usuario" toca todas: es la que queda difícil.
DROP TABLE IF EXISTS mensajes;
CREATE TABLE mensajes (
    chat_id BIGINT NOT NULL, mensaje_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL,
    enviado_en TIMESTAMPTZ NOT NULL DEFAULT now(), cuerpo TEXT NOT NULL,
    PRIMARY KEY (chat_id, mensaje_id)
) PARTITION BY HASH (chat_id);
CREATE TABLE mensajes_p0 PARTITION OF mensajes FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE mensajes_p1 PARTITION OF mensajes FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE mensajes_p2 PARTITION OF mensajes FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE mensajes_p3 PARTITION OF mensajes FOR VALUES WITH (MODULUS 4, REMAINDER 3);
INSERT INTO mensajes SELECT (g % 2000) + 1, g, (g % 500) + 1, now(), 'hola' FROM generate_series(1, 200000) g;
ANALYZE mensajes;
