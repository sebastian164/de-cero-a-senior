# 01 — Modelado relacional y normalización

## Idea

Si la dirección de un cliente está escrita en cada una de sus cien facturas, el día que se mude habrá que corregir cien registros, y alguno quedará mal. Normalizar es guardar cada dato una sola vez y referenciarlo.

## Lo esencial

- **Tabla, fila, columna, clave primaria** (identifica la fila), **clave foránea** (referencia otra tabla y garantiza que exista).
- **Relaciones:** uno a uno, uno a muchos (clave foránea en el lado "muchos"), muchos a muchos (tabla intermedia).
- **Restricciones:** `NOT NULL`, `UNIQUE`, `CHECK`, `FOREIGN KEY`. La base de datos es la última línea de defensa de la integridad: las validaciones de la aplicación se pueden saltar; las restricciones no.

### Formas normales

| Forma | Regla | Señal de incumplimiento |
|---|---|---|
| 1FN | Valores atómicos, sin grupos repetidos | Columnas `telefono1`, `telefono2`; listas separadas por comas |
| 2FN | Todo atributo depende de la clave completa | En una clave compuesta, un dato que depende solo de una parte |
| 3FN | Ningún atributo depende de otro atributo no clave | `ciudad` y `departamento` en la tabla de clientes |

Regla mnemotécnica: cada dato depende de la clave, de toda la clave y de nada más que la clave.

### Desnormalizar

Se hace a propósito y con datos que lo justifiquen: para evitar uniones costosas en lecturas muy frecuentes, o para **conservar un valor histórico**. El precio unitario se copia en la línea del pedido: si el producto cambia de precio mañana, la factura de ayer no debe cambiar. Eso no es redundancia; es un hecho distinto.

### Decisiones recurrentes

- **Clave natural o sustituta:** una clave sustituta (`BIGINT` autogenerado o UUID) es estable; la natural (cédula, correo) puede cambiar. Lo habitual: sustituta como primaria y `UNIQUE` sobre la natural.
- **UUID:** útiles en sistemas distribuidos; los aleatorios fragmentan los índices. Los ordenados por tiempo (UUIDv7) mitigan el problema.
- **Dinero:** `NUMERIC(precisión, escala)`, nunca coma flotante.
- **Fechas:** con zona horaria (`TIMESTAMPTZ`), almacenadas en UTC.
- **Borrado lógico** (`eliminado_en`): útil para auditoría, complica consultas y unicidad.

## Ejemplo

```sql
CREATE TABLE clientes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento   VARCHAR(20)  NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL
);

CREATE TABLE pedidos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id  BIGINT       NOT NULL REFERENCES clientes(id),
    estado      VARCHAR(20)  NOT NULL CHECK (estado IN ('CREADO','PAGADO','ENVIADO','CANCELADO')),
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE productos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      VARCHAR(120)  NOT NULL,
    precio      NUMERIC(12,2) NOT NULL CHECK (precio >= 0)
);

CREATE TABLE lineas_pedido (
    pedido_id       BIGINT        NOT NULL REFERENCES pedidos(id),
    producto_id     BIGINT        NOT NULL REFERENCES productos(id),
    cantidad        INT           NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12,2) NOT NULL,       -- copia histórica, a propósito
    PRIMARY KEY (pedido_id, producto_id)
);
```

## Errores comunes

- Columnas con listas separadas por comas o JSON para evitar una tabla.
- Sin claves foráneas "porque la aplicación valida".
- Coma flotante para dinero.
- Una tabla gigante con 80 columnas, la mayoría nulas.
- Diseñar las tablas a partir de las pantallas.
- Nombres inconsistentes (`cliente_id`, `idCliente`, `cod_cli`).
- Modelo EAV (entidad-atributo-valor) genérico para todo.

## Taller

Modela una plataforma de reservas de canchas deportivas: sedes, canchas, tipos de deporte, tarifas por franja horaria, clientes, reservas, pagos y cancelaciones.

1. Dibuja el diagrama entidad-relación.
2. Escribe el DDL con todas las restricciones. Incluye una que impida dos reservas de la misma cancha en la misma franja.
3. Señala qué valores copiaste a propósito para conservar la historia.
4. Indica una decisión de desnormalización que tomarías y con qué evidencia.
5. Mapea dos de las tablas a entidades JPA y señala dónde el modelo de objetos y el relacional no encajan.

Rama `taller/n8-01-modelado`.

## Preguntas senior

1. ¿Cuándo desnormalizar y qué riesgo se asume?
2. Clave natural frente a sustituta; entero frente a UUID.
3. ¿Por qué mantener las restricciones en la base si la aplicación ya valida?
4. ¿Cómo modelas datos históricos que no deben cambiar?
5. ¿Qué es el desajuste de impedancia entre objetos y tablas?
