create table if not exists cuenta (
    id              varchar(64)  primary key,
    titular         varchar(120) not null,
    saldo_centavos  bigint       not null check (saldo_centavos >= 0),
    version         bigint       not null
);

create table if not exists movimiento (
    id          bigint generated always as identity primary key,
    cuenta_id   varchar(64)  not null references cuenta (id),
    tipo        varchar(32)  not null,
    centavos    bigint       not null check (centavos > 0),
    fecha       timestamp with time zone not null,
    referencia  varchar(120) not null
);
