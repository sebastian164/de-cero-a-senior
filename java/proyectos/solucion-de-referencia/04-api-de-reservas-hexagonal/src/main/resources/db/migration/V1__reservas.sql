CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservas (
    id      uuid PRIMARY KEY,
    espacio varchar(60) NOT NULL,
    usuario varchar(60) NOT NULL,
    inicio  timestamptz NOT NULL,
    fin     timestamptz NOT NULL,
    estado  varchar(12) NOT NULL CHECK (estado IN ('ACTIVA', 'CANCELADA')),
    CHECK (fin > inicio),
    -- La garantía contra la doble reserva vive en la base: ni dos hilos ni dos instancias pueden saltársela.
    CONSTRAINT sin_solape EXCLUDE USING gist (espacio WITH =, tstzrange(inicio, fin) WITH &&) WHERE (estado = 'ACTIVA')
);

CREATE TABLE claves_idempotencia (
    clave      varchar(80) NOT NULL,
    usuario    varchar(60) NOT NULL,
    reserva_id uuid NOT NULL REFERENCES reservas (id),
    PRIMARY KEY (clave, usuario)
);

CREATE TABLE lista_espera (
    id      bigserial PRIMARY KEY,
    espacio varchar(60) NOT NULL,
    usuario varchar(60) NOT NULL,
    inicio  timestamptz NOT NULL,
    fin     timestamptz NOT NULL
);
