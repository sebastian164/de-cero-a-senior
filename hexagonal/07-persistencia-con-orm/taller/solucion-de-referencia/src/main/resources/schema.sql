create table if not exists producto (
    id               varchar(64) primary key,
    precio_centavos  bigint      not null check (precio_centavos >= 0)
);

-- Repite la regla "no hay existencias negativas": última defensa si alguien escribe sin pasar por el dominio.
create table if not exists existencias (
    producto  varchar(64) primary key references producto (id),
    unidades  integer     not null check (unidades >= 0),
    version   bigint      not null
);

create table if not exists pedido (
    id         varchar(64)  primary key,
    cliente    varchar(120) not null check (cliente <> ''),
    estado     varchar(32)  not null,
    creado_en  timestamp with time zone not null,
    version    bigint       not null
);

create index if not exists pedido_por_cliente on pedido (cliente, creado_en);

create table if not exists linea_de_pedido (
    id                        bigint generated always as identity primary key,
    pedido_id                 varchar(64) not null references pedido (id),
    posicion                  integer     not null,
    producto                  varchar(64) not null,
    cantidad                  integer     not null check (cantidad >= 1),
    precio_unitario_centavos  bigint      not null check (precio_unitario_centavos >= 0),
    unique (pedido_id, posicion)
);

-- La clave primaria impide crear dos pedidos con la misma clave de idempotencia.
create table if not exists peticion_procesada (
    clave      varchar(120) primary key,
    pedido_id  varchar(64)  not null references pedido (id)
);

create table if not exists evento_pendiente (
    id           varchar(160) primary key,
    tipo         varchar(64)  not null,
    pedido_id    varchar(64)  not null,
    cliente      varchar(120) not null,
    ocurrido_en  timestamp with time zone not null
);

insert into producto (id, precio_centavos) values ('teclado', 15000000), ('raton', 6000000), ('monitor', 90000000)
    on conflict do nothing;
insert into existencias (producto, unidades, version) values ('teclado', 10, 0), ('raton', 3, 0), ('monitor', 2, 0)
    on conflict do nothing;
