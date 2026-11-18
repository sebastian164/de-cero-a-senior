create table if not exists pedido (
    id varchar(40) primary key, cliente varchar(60) not null,
    estado varchar(30) not null check (estado in ('REQUIERE_APROBACION','PENDIENTE','CONFIRMADO','CANCELADO')),
    creado_en timestamptz not null default now());
create table if not exists linea_pedido (
    id bigint generated always as identity primary key, pedido_id varchar(40) not null references pedido (id),
    producto varchar(40) not null, cantidad int not null check (cantidad > 0), precio_centavos bigint not null check (precio_centavos >= 0));
create table if not exists existencias (producto varchar(40) primary key, cantidad int not null check (cantidad >= 0));
