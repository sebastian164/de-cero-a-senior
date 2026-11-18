-- Datos de las mediciones y pruebas. Se puede repetir: deja todo como al principio.
truncate clave_idempotencia, linea_pedido, pedido, existencias cascade;
insert into existencias select 'prod-' || g, 1000000 from generate_series(1, 5) g;
insert into pedido select 'm' || g from generate_series(1, 400) g;
insert into linea_pedido (pedido_id, producto, cantidad)
    select 'm' || g, 'prod-' || p, 1 from generate_series(1, 400) g, generate_series(1, 3) p;
