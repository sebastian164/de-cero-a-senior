-- Taller n8-01: plataforma de reservas de canchas deportivas.
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- para combinar igualdad (cancha) y solapamiento (franja) en una restricción

CREATE TABLE sedes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre     VARCHAR(120) NOT NULL UNIQUE,
    direccion  VARCHAR(200) NOT NULL,
    zona_horaria VARCHAR(50) NOT NULL DEFAULT 'America/Bogota'
);

CREATE TABLE deportes (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE canchas (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sede_id     BIGINT      NOT NULL REFERENCES sedes(id),
    deporte_id  BIGINT      NOT NULL REFERENCES deportes(id),
    nombre      VARCHAR(60) NOT NULL,
    activa      BOOLEAN     NOT NULL DEFAULT true,
    UNIQUE (sede_id, nombre),
    UNIQUE (id, sede_id)                     -- permite la clave foránea compuesta de reservas (desnormalización controlada)
);
CREATE INDEX idx_canchas_deporte ON canchas (deporte_id);

-- Tarifa por franja horaria y día de la semana, con vigencia: el precio cambia en el tiempo.
CREATE TABLE tarifas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cancha_id     BIGINT        NOT NULL REFERENCES canchas(id),
    dia_semana    SMALLINT      NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),   -- ISO: 1 = lunes
    hora_inicio   TIME          NOT NULL,
    hora_fin      TIME          NOT NULL,
    precio_hora   NUMERIC(12,2) NOT NULL CHECK (precio_hora >= 0),
    vigente_desde DATE          NOT NULL,
    vigente_hasta DATE,
    CHECK (hora_fin > hora_inicio),
    CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
    -- dos tarifas vigentes de la misma cancha no se pueden pisar en el mismo día y horario
    EXCLUDE USING gist (
        cancha_id WITH =,
        dia_semana WITH =,
        tsrange('2000-01-01'::date + hora_inicio, '2000-01-01'::date + hora_fin) WITH &&,
        daterange(vigente_desde, vigente_hasta) WITH &&
    )
);

CREATE TABLE clientes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento  VARCHAR(20)  NOT NULL UNIQUE,
    nombre     VARCHAR(120) NOT NULL,
    correo     VARCHAR(160) NOT NULL,
    telefono   VARCHAR(30)
);
CREATE UNIQUE INDEX uq_clientes_correo ON clientes (lower(correo));

CREATE TABLE reservas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cancha_id     BIGINT        NOT NULL,
    sede_id       BIGINT        NOT NULL,              -- desnormalizado: agenda diaria por sede sin unir con canchas
    cliente_id    BIGINT        NOT NULL REFERENCES clientes(id),
    tarifa_id     BIGINT        NOT NULL REFERENCES tarifas(id),
    inicio        TIMESTAMPTZ   NOT NULL,
    fin           TIMESTAMPTZ   NOT NULL,
    precio_total  NUMERIC(12,2) NOT NULL CHECK (precio_total >= 0),   -- copia histórica: lo acordado al reservar
    estado        VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE_PAGO'
                  CHECK (estado IN ('PENDIENTE_PAGO','CONFIRMADA','CANCELADA','JUGADA','NO_ASISTIO')),
    creada_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    CHECK (fin > inicio),
    FOREIGN KEY (cancha_id, sede_id) REFERENCES canchas (id, sede_id),   -- la sede copiada no puede contradecir a la cancha
    -- la regla del enunciado: nunca dos reservas vivas de la misma cancha que se solapen en el tiempo
    CONSTRAINT reservas_sin_solapamiento EXCLUDE USING gist (
        cancha_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA')
);
CREATE INDEX idx_reservas_cliente ON reservas (cliente_id, inicio DESC);
CREATE INDEX idx_reservas_sede_dia ON reservas (sede_id, inicio);
CREATE INDEX idx_reservas_tarifa ON reservas (tarifa_id);

CREATE TABLE pagos (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id     BIGINT        NOT NULL REFERENCES reservas(id),
    monto          NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    metodo         VARCHAR(20)   NOT NULL CHECK (metodo IN ('TARJETA','PSE','EFECTIVO','NEQUI')),
    estado         VARCHAR(20)   NOT NULL CHECK (estado IN ('APROBADO','RECHAZADO','REEMBOLSADO')),
    referencia     VARCHAR(80)   NOT NULL UNIQUE,                  -- la de la pasarela: evita registrar dos veces el mismo pago
    pagado_en      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pagos_reserva ON pagos (reserva_id);

CREATE TABLE cancelaciones (
    reserva_id          BIGINT PRIMARY KEY REFERENCES reservas(id),  -- una reserva se cancela una sola vez
    motivo              VARCHAR(200)  NOT NULL,
    cancelada_por       VARCHAR(20)   NOT NULL CHECK (cancelada_por IN ('CLIENTE','SEDE')),
    porcentaje_reembolso NUMERIC(5,2) NOT NULL CHECK (porcentaje_reembolso BETWEEN 0 AND 100),  -- copia de la política vigente
    monto_reembolso     NUMERIC(12,2) NOT NULL CHECK (monto_reembolso >= 0),
    cancelada_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
