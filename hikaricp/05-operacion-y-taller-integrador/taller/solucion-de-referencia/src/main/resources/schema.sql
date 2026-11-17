create table if not exists tablero (id int primary key, valor bigint not null);
insert into tablero (id, valor) select n, 0 from generate_series(1, 1000) n on conflict do nothing;
