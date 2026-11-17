-- Cada módulo es dueño de sus tablas (prefijo del módulo). Ningún módulo toca las de otro.
CREATE TABLE catalogo_productos (sku varchar(40) PRIMARY KEY, nombre varchar(120) NOT NULL, precio numeric(12,2) NOT NULL CHECK (precio > 0));

CREATE TABLE inventario_stock (sku varchar(40) PRIMARY KEY, disponible int NOT NULL CHECK (disponible >= 0));
CREATE TABLE inventario_reservas (pedido_id bigint PRIMARY KEY, sku varchar(40) NOT NULL, cantidad int NOT NULL, liberada boolean NOT NULL DEFAULT false);

CREATE TABLE pedidos_pedidos (
    id bigserial PRIMARY KEY, cliente varchar(60) NOT NULL, sku varchar(40) NOT NULL, cantidad int NOT NULL,
    total numeric(12,2) NOT NULL, estado varchar(12) NOT NULL, motivo varchar(200), creado timestamptz NOT NULL DEFAULT now());
-- Justificado con EXPLAIN en TiendaTest (consulta "mis pedidos" con EXPLAIN): "mis pedidos" pasa de Seq Scan + Sort a Index Scan sin Sort.
CREATE INDEX pedidos_por_cliente ON pedidos_pedidos (cliente, creado DESC);

CREATE TABLE pagos_cobros (pedido_id bigint PRIMARY KEY, monto numeric(12,2) NOT NULL, estado varchar(12) NOT NULL);

-- Núcleo compartido de eventos: la bandeja de salida (Outbox).
CREATE TABLE eventos_outbox (
    id bigserial PRIMARY KEY, tipo varchar(40) NOT NULL, clave varchar(60) NOT NULL, carga text NOT NULL,
    creado timestamptz NOT NULL DEFAULT now(), publicado boolean NOT NULL DEFAULT false);
-- El relevo solo mira lo pendiente, que es una fracción mínima de la tabla.
CREATE INDEX outbox_pendientes ON eventos_outbox (id) WHERE NOT publicado;

CREATE TABLE notificaciones_procesadas (evento_id bigint PRIMARY KEY);
CREATE TABLE notificaciones_enviadas (id bigserial PRIMARY KEY, evento_id bigint NOT NULL, pedido_id bigint NOT NULL, mensaje varchar(200) NOT NULL);
