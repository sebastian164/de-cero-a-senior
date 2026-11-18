truncate linea_pedido, pedido, existencias cascade;
insert into existencias select 'prod-' || g, 10000000 from generate_series(1, 20) g;
insert into pedido (id, cliente, estado) select 'm' || g, 'cliente-' || (g % 10), 'PENDIENTE' from generate_series(1, 1000) g;
insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos)
    select 'm' || g, 'prod-' || ((g % 20) + 1), 1, 1000 from generate_series(1, 1000) g;
insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos)
    select 'm' || g, 'prod-' || (((g + 7) % 20) + 1), 1, 2000 from generate_series(1, 1000) g;
