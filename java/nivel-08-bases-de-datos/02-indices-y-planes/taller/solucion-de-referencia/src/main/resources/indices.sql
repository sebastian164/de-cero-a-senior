-- Índices mínimos para las cinco consultas.
-- I1 (Q1, Q4): igualdad por cliente primero, orden por fecha después. INCLUDE permite recorridos de solo índice.
CREATE INDEX idx_pedidos_cliente_fecha ON pedidos (cliente_id, creado_en DESC) INCLUDE (estado, total);
-- I2 (Q2): la consulta compara lower(correo); el índice debe ser sobre la misma expresión.
CREATE INDEX idx_pedidos_correo_lower ON pedidos (lower(correo));
-- I3 (Q3): rango por fecha para el reporte diario.
CREATE INDEX idx_pedidos_creado_en ON pedidos (creado_en);
-- I4 (Q5): solo el 2 % de las filas está pendiente. Parcial: pequeño y exacto.
CREATE INDEX idx_pedidos_pendientes ON pedidos (creado_en) WHERE estado = 'PENDIENTE';
ANALYZE pedidos;
