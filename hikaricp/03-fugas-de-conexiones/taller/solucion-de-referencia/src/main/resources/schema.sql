create table if not exists cliente (
    id       bigint primary key,
    nombre   text   not null,
    visitas  int    not null default 0
);
insert into cliente (id, nombre) select n, 'cliente ' || n from generate_series(1, 100) n on conflict do nothing;
