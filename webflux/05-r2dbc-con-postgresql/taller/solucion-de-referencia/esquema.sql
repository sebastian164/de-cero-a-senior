-- Taller 05: esquema escrito a mano (PostgreSQL).
create table if not exists pedido (
    id      varchar(40) primary key,
    estado  varchar(20) not null default 'PENDIENTE' check (estado in ('PENDIENTE','CONFIRMADO','RECHAZADO'))
);
create table if not exists linea_pedido (
    id         bigint generated always as identity primary key,
    pedido_id  varchar(40) not null references pedido (id),
    producto   varchar(40) not null,
    cantidad   int not null check (cantidad > 0)
);
create index if not exists linea_por_pedido on linea_pedido (pedido_id);
create table if not exists existencias (
    producto  varchar(40) primary key,
    cantidad  int not null check (cantidad >= 0)       -- la base también defiende el invariante
);
create table if not exists clave_idempotencia (
    clave      varchar(80) primary key,
    pedido_id  varchar(40) not null references pedido (id),
    creada     timestamptz not null default now()
);
