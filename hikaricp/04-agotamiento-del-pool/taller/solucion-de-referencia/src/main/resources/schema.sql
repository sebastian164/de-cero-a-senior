create table if not exists pedido (
    id            bigint primary key,
    estado        varchar(20) not null,
    total         bigint      not null,
    confirmaciones int        not null default 0
);
create table if not exists linea (
    id         bigint generated always as identity primary key,
    pedido_id  bigint not null references pedido (id),
    producto   varchar(40) not null,
    cantidad   int not null
);
insert into pedido (id, estado, total) select n, 'PENDIENTE', n * 1000 from generate_series(1, 1000) n on conflict do nothing;
insert into linea (pedido_id, producto, cantidad)
    select p, 'producto ' || l, l from generate_series(1, 1000) p, generate_series(1, 3) l
    where not exists (select 1 from linea);
