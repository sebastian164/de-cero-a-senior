create table if not exists cuenta (
    id                    varchar(64)  primary key,
    titular               varchar(120) not null,
    saldo_centavos        bigint       not null check (saldo_centavos >= 0),
    enviado_hoy_centavos  bigint       not null check (enviado_hoy_centavos >= 0),
    dia_del_tope          date         not null,
    version               bigint       not null
);

create table if not exists movimiento (
    id          bigint generated always as identity primary key,
    cuenta_id   varchar(64)  not null references cuenta (id),
    tipo        varchar(32)  not null,
    centavos    bigint       not null check (centavos > 0),
    fecha       timestamp with time zone not null,
    referencia  varchar(120) not null
);

create index if not exists movimiento_por_cuenta on movimiento (cuenta_id, id);

-- La clave primaria es lo que impide atender dos veces la misma orden.
create table if not exists orden_procesada (
    clave  varchar(120) primary key
);

-- La bandeja de salida.
create table if not exists evento_pendiente (
    id           varchar(160) primary key,
    tipo         varchar(64)  not null,
    cuenta_id    varchar(64)  not null,
    centavos     bigint       not null,
    ocurrido_en  timestamp with time zone not null
);
