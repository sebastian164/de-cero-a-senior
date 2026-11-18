-- Se puede repetir: deja todo como al principio.
truncate linea_pedido, pedido, existencias cascade;
insert into existencias select 'prod-' || g, 1000000 from generate_series(1, 5) g;
insert into pedido (id, cliente, estado) select 'm' || g, 'cliente-' || (g % 10), 'PENDIENTE' from generate_series(1, 400) g;
insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos)
    select 'm' || g, 'prod-' || p, 1, 1000 * p from generate_series(1, 400) g, generate_series(1, 3) p;
