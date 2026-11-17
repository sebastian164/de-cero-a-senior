CREATE TABLE IF NOT EXISTS stock(producto_id text PRIMARY KEY, existencias int NOT NULL CHECK (existencias >= 0));
CREATE TABLE IF NOT EXISTS procesados(evento_id text PRIMARY KEY, resultado text);
